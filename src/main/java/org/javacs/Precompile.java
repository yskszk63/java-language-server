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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Precompile everything on the source path so that other operations are fast
 */
class Precompile {
    private final Set<Path> sourcePath, classPath;
    private final Path outputDirectory;
    private final JavacTool javac = JavacTool.create();
    private final JavacFileManager fileManager;
    private final IncrementalFileManager incremental;

    Precompile(Set<Path> sourcePath, Set<Path> classPath, Path outputDirectory) {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
        this.outputDirectory = outputDirectory;
        this.fileManager = javac.getStandardFileManager(this::onError, Locale.getDefault(), Charset.defaultCharset());

        Set<File> sourcePathFiles = sourcePath.stream().map(Path::toFile).collect(Collectors.toSet());

        try {
            if (!sourcePathFiles.isEmpty())
                fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePathFiles);

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(outputDirectory.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.incremental = new IncrementalFileManager(fileManager);

        Runnable precompileAll = () -> {
            try {
                // TODO send a progress bar to the user
                Set<URI> all = SymbolIndex.allJavaSources(sourcePath);

                for (URI each : all) {
                    if (needsCompile(each)) 
                        precompile(each);
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Uncaught exception in precompile thread", e);
            }
        };

        new Thread(precompileAll, "Precompile").start();
    }

    private boolean needsCompile(URI file) {
        return qualifiedName(file)
            .map(name -> !incremental.hasUpToDateClassFile(name))
            .orElse(true);
    }

    private Optional<String> qualifiedName(URI file) {
        Path path = Paths.get(file);

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

    private void precompile(URI file) {
        LOG.info("Precompile " + file);
        
        try {
            JavaFileObject fileObject = fileManager.getRegularFile(Paths.get(file).toFile());
            List<String> options = JavacHolder.options(sourcePath, classPath, outputDirectory, true);
            JavacTask task = javac.getTask(null, fileManager, this::onError, options, null, Collections.singletonList(fileObject));

            task.call();
        } catch (Exception e) {
            LOG.warning("Failed to compile " + file + " with " + e.getMessage());
        }
    }

    private void onError(Diagnostic<? extends JavaFileObject> err) {
        // Ignore all errors
    }

    private static Logger LOG = Logger.getLogger("main");
}