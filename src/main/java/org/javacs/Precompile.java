package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Precompile everything on the source path so that other operations are fast
 */
class Precompile {
    private final Set<Path> sourcePath, classPath;
    private final Path workspaceRoot, outputDirectory;
    private final JavacTool javac = JavacTool.create();
    private final JavacFileManager fileManager;
    private final IncrementalFileManager incremental;
    private final Progress reportProgress;

    Precompile(Path workspaceRoot, Set<Path> sourcePath, Set<Path> classPath, Path outputDirectory, Progress reportProgress) {
        this.workspaceRoot = workspaceRoot;
        this.sourcePath = sourcePath;
        this.classPath = classPath;
        this.outputDirectory = outputDirectory;
        this.fileManager = javac.getStandardFileManager(this::onError, Locale.getDefault(), Charset.defaultCharset());
        this.reportProgress = reportProgress;

        Set<File> sourcePathFiles = sourcePath.stream().map(Path::toFile).collect(Collectors.toSet());

        try {
            if (!sourcePathFiles.isEmpty())
                fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePathFiles);

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDirectory.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.incremental = new IncrementalFileManager(fileManager);
    }

    /**
     * Precompile all files on a new thread
     */
    void start() {
        new Thread(this::run, "Precompile").start();
    }

    /**
     * Precompile all files synchronously, blocking the current thread.
     * 
     * Useful for testing.
     */
    void run() {
        try {
            Function<Path, Set<Path>> javaSources = dir -> InferConfig.allJavaFiles(dir).collect(Collectors.toSet());
            Map<Path, Set<Path>> javaSourcesBySourceRoot = sourcePath.stream().collect(Collectors.toMap(dir -> dir, javaSources));
            List<Set<Path>> retry = new ArrayList<>();
            int completed = 0, total = javaSourcesBySourceRoot.values().stream().mapToInt(files -> files.size()).sum();

            // Try to compile each source root in 1 operation
            List<Path> order = javaSourcesBySourceRoot.keySet().stream().sorted().collect(Collectors.toList());

            for (Path sourceRoot : order) {
                Set<Path> allInRoot = javaSourcesBySourceRoot.get(sourceRoot);
                Set<Path> todo = allInRoot.stream().filter(Precompile.this::needsCompile).collect(Collectors.toSet());

                if (!todo.isEmpty()) {
                    LOG.info("Precompile " + todo.size() + " files in " + sourceRoot);

                    reportProgress.report(workspaceRoot.relativize(sourceRoot).toString(), completed, total);

                    if (precompile(todo))
                        completed += allInRoot.size();
                    else // If compilation fails, we will retry each .java file individually
                        retry.add(allInRoot);
                }
            }

            // When compiling the entire source root fails, try to compile each .java file individually
            for (Set<Path> retryDir : retry) {
                for (Path retryFile : retryDir) {
                    reportProgress.report(retryFile.getFileName().toString(), completed, total);

                    precompile(Collections.singleton(retryFile));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Uncaught exception in precompile thread", e);
        } finally {
            reportProgress.done();
        }
    }

    private boolean needsCompile(Path path) {
        return qualifiedName(path)
            .map(name -> !incremental.hasUpToDateClassFile(name))
            .orElse(true);
    }

    private Optional<String> qualifiedName(Path path) {
        return sourcePath.stream()
            .filter(dir -> path.startsWith(dir))
            .map(dir -> dir.relativize(path))
            .flatMap(this::guessQualifiedName)
            .findFirst();
    }

    private Stream<String> guessQualifiedName(Path relative) {
        StringJoiner result = new StringJoiner(".");
        Path parent = relative.getParent();

        if (parent != null) {
            for (Path name : parent)
                result.add(name.toString());
        }
        
        String fileName = relative.getFileName().toString();

        if (!fileName.endsWith(".java"))
            return Stream.empty();
        
        result.add(fileName.substring(0, fileName.length() - ".java".length()));

        return Stream.of(result.toString());
    }

    private boolean precompile(Set<Path> files) {
        try {
            List<JavaFileObject> fileObjects = files.stream().map(file -> fileManager.getRegularFile(file.toFile())).collect(Collectors.toList());
            List<String> options = JavacHolder.options(sourcePath, classPath, outputDirectory, true);
            JavacTask task = javac.getTask(null, fileManager, this::onError, options, null, fileObjects);

            // TODO prune all method bodies after parse, before compile, to make it go faster
            return task.call();
        } catch (Exception e) {
            LOG.warning("Failed to compile " + files.size() + " files with " + e.getMessage());

            return false;
        }
    }

    private void onError(Diagnostic<? extends JavaFileObject> err) {
        if (err.getKind() == Diagnostic.Kind.ERROR)
            LOG.warning(err.getMessage(Locale.getDefault()));
    }

    private static Logger LOG = Logger.getLogger("main");

    interface Progress {
        /**
         * Report progress on precompiling files back to the UI
         */
        void report(String currentFileName, int completed, int total);

        void done();

        static Progress EMPTY = new Progress() {
            public void report(String currentFileName, int completed, int total) {

            }

            public void done() {
                
            }
        };
    }
}