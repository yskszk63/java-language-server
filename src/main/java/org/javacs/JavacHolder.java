package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Options;
import org.eclipse.lsp4j.SymbolInformation;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Maintains a reference to a Java compiler, 
 * and several of its internal data structures,
 * which we need to fiddle with to get incremental compilation 
 * and extract the diagnostic information we want.
 */
public class JavacHolder {

    public static JavacHolder create(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        return new JavacHolder(classPath, sourcePath, outputDirectory, true);
    }

    public static JavacHolder createWithoutIndex(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        return new JavacHolder(classPath, sourcePath, outputDirectory, false);
    }

    /**
     * Compile a single file, without updating the index.
     *
     * As an optimization, this function may ignore code not accessible to the cursor.
     */
    public FocusedResult compileFocused(URI file, Optional<String> textContent, int line, int column, boolean pruneStatements) {
        initialIndexComplete.join();

        JavaFileObject fileObject = findFile(file, textContent);

        if (pruneStatements)
            fileObject = TreePruner.putSemicolonAfterCursor(fileObject, line, column);

        JavacTask task = createTask(Collections.singleton(fileObject));

        try {
            Iterable<? extends CompilationUnitTree> parse = task.parse();

            if (pruneStatements) {
                TreePruner pruner = new TreePruner(task);

                for (CompilationUnitTree tree : parse) {
                    pruner.removeNonCursorMethodBodies(tree, line, column);
                    pruner.removeStatementsAfterCursor(tree, line, column);
                }
            }

            try {
                Iterable<? extends Element> analyze = task.analyze();
            } catch (AssertionError e) {
                if (!catchJavacError(e))
                    throw e;
            }

            Supplier<Stream<? extends CompilationUnitTree>> compilationUnits = () -> StreamSupport.stream(parse.spliterator(), false);
            Optional<TreePath> cursor = compilationUnits.get()
                    .flatMap(source -> stream(FindCursor.find(task, source, line, column)))
                    .findAny();

            return new FocusedResult(cursor, task, classPathIndex, index);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Clear files and all their dependents, recompile, compileBatch the index, and report any errors.
     *
     * If these files reference un-compiled dependencies, those dependencies will also be parsed and compiled.
     */
    public BatchResult compileBatch(Map<URI, Optional<String>> files) {
        initialIndexComplete.join();

        return doCompile(files);
    }

    private static final Logger LOG = Logger.getLogger("main");
    /**
     * Where this javac looks for library .class files
     */
    public final Set<Path> classPath;
    /**
     * Where this javac looks for .java source files
     */
    public final Set<Path> sourcePath;
    /**
     * Where this javac places generated .class files
     */
    public final Path outputDirectory;

    /**
     * Javac tool creates a new Context every time we do createTask(...), so maintaining a reference to it doesn't really do anything
     */
    private final JavacTool javac = JavacTool.create();

    /**
     * Keep the file manager around because it hangs onto all our .class files
     */
    private final JavacFileManager fileManager = javac.getStandardFileManager(this::onError, null, Charset.defaultCharset());

    /**
     * javac isn't friendly to swapping out the error-reporting DiagnosticListener,
     * so we install this intermediate DiagnosticListener, which forwards to errorsDelegate
     */
    private void onError(Diagnostic<? extends JavaFileObject> diagnostic) {
        onErrorDelegate.report(diagnostic);
    }

    /**
     * Error reporting initially goes nowhere.
     * We will replace this with a function that collects errors so we can report all the errors associated with a file apply once.
     */
    private DiagnosticListener<JavaFileObject> onErrorDelegate = diagnostic -> {};

    // Set up SymbolIndex

    /**
     * Index of symbols that gets updated every time you call compileBatch
     */
    public final SymbolIndex index;

    // TODO pause indexing rather than waiting for it to finish
    /**
     * Completes when initial index is done. Useful for testing.
     */
    private final CompletableFuture<Void> initialIndexComplete;

    private final List<String> options;

    public final ClassPathIndex classPathIndex;

    private JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory, boolean index) {
        this.classPath = Collections.unmodifiableSet(classPath);
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.outputDirectory = outputDirectory;
        this.options = options(classPath, sourcePath, outputDirectory);
        this.index = new SymbolIndex();
        this.initialIndexComplete = index ? startIndexingSourcePath() : CompletableFuture.completedFuture(null);
        this.classPathIndex = new ClassPathIndex(classPath);

        ensureOutputDirectory(outputDirectory);
        clearOutputDirectory(outputDirectory);
    }

    static List<String> options(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        return ImmutableList.of(
                "-classpath", Joiner.on(File.pathSeparator).join(classPath),
                "-sourcepath", Joiner.on(File.pathSeparator).join(sourcePath),
                "-d", outputDirectory.toString(),
                // You would think we could do -Xlint:all,
                // but some lints trigger fatal errors in the presence of parse errors
                "-Xlint:cast",
                "-Xlint:deprecation",
                "-Xlint:empty",
                "-Xlint:fallthrough",
                "-Xlint:finally",
                "-Xlint:path",
                "-Xlint:unchecked",
                "-Xlint:varargs",
                "-Xlint:static"
        );
    }

    private JavacTask createTask(Collection<JavaFileObject> files) {
        JavacTask result = javac.getTask(null, fileManager, this::onError, options, null, files);
        JavacTaskImpl impl = (JavacTaskImpl) result;
        Options options = Options.instance(impl.getContext());

        options.put("dev", "");

        return result;
    }

    /**
     * Index exported declarations and references for all files on the source path
     * This may take a while, so we'll do it on an extra thread
     */
    private CompletableFuture<Void> startIndexingSourcePath() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                List<URI> objects = new ArrayList<>();

                // Parse each file
                sourcePath.forEach(s -> findAllFiles(s, objects));

                // Compile all parsed files
                Map<URI, Optional<String>> files = objects.stream().collect(Collectors.toMap(key -> key, key -> Optional.empty()));
                BatchResult result = doCompile(files);

                // TODO minimize memory use during this process
                // Instead of doing parse-all / compileFileObjects-all,
                // queue all files, then do parse / compileFileObjects on each
                // If invoked correctly, javac should avoid reparsing the same file twice
                // Then, use the same mechanism as the desugar / generate phases to remove method bodies,
                // to reclaim memory as we go

                // TODO verify that compiler and all its resources get destroyed
            }

            /**
             * Look for .java files and invalidate them
             */
            private void findAllFiles(Path path, List<URI> uris) {
                if (Files.isDirectory(path)) try {
                    Files.list(path).forEach(p -> findAllFiles(p, uris));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                else if (path.getFileName().toString().endsWith(".java")) {
                    LOG.info("Index " + path);

                    uris.add(path.toUri());
                }
            }
        }, command -> new Thread(command, "InitialIndex").start());
    }

    /** 
     * Ensure output directory exists 
     */
    private void ensureOutputDirectory(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw ShowMessageException.error("Error created output directory " + dir, null);
            }
        }
        else if (!Files.isDirectory(dir))
            throw ShowMessageException.error("Output directory " + dir + " is not a directory", null);
    }

    /** 
     * Set all .class files to modified-apply 1970 so javac won't skip them when we invoke it
     */
    private static void clearOutputDirectory(Path file) {
        try {
            if (file.getFileName().toString().endsWith(".class")) {
                LOG.info("Invalidate " + file);

                Files.setLastModifiedTime(file, FileTime.from(Instant.EPOCH));
            }
            else if (Files.isDirectory(file))
                Files.list(file).forEach(JavacHolder::clearOutputDirectory);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private static <T> Stream<T> stream(Optional<T> option) {
        return option.map(Stream::of).orElseGet(Stream::empty);
    }

    public Stream<SymbolInformation> searchWorkspace(String query) {
        initialIndexComplete.join();

        return index.search(query);
    }

    public Stream<SymbolInformation> searchFile(URI file) {
        initialIndexComplete.join();

        return index.allInFile(file);
    }

    /**
     * File has been deleted
     */
    // TODO delete multiple objects apply the same time for performance if user does that
    public BatchResult delete(URI uri) {
        initialIndexComplete.join();

        JavaFileObject object = findFile(uri, Optional.empty());
        Map<URI, Optional<String>> deps = dependencies(Collections.singleton(object))
                .stream()
                .collect(Collectors.toMap(o -> o.toUri(), o -> Optional.empty()));

        index.clear(uri);

        return compileBatch(deps);
    }

    // TODO this should return Optional.empty() file URI is not file: and text is empty
    private JavaFileObject findFile(URI file, Optional<String> text) {
        return text
                .map(content -> (JavaFileObject) new StringFileObject(content, file))
                .orElseGet(() -> fileManager.getRegularFile(Paths.get(file).toFile()));
    }

    private DiagnosticCollector<JavaFileObject> startCollectingErrors() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();

        onErrorDelegate = error -> {
            if (error.getStartPosition() != Diagnostic.NOPOS)
                errors.report(error);
            else
                LOG.warning("Skipped " + error.getMessage(null));
        };
        return errors;
    }

    private void stopCollectingErrors() {
        onErrorDelegate = error -> {};
    }

    public ParseResult parse(URI file, Optional<String> textContent, DiagnosticListener<JavaFileObject> onError) {
        JavaFileObject object = findFile(file, textContent);
        JavacTask task = createTask(Collections.singleton(object));
        onErrorDelegate = onError;

        try {
            List<CompilationUnitTree> trees = Lists.newArrayList(task.parse());

            if (trees.isEmpty())
                throw new RuntimeException("Compiling " + file + " produced 0 results");
            else if (trees.size() == 1)
                return new ParseResult(task, trees.get(0));
            else
                throw new RuntimeException("Compiling " + file + " produced " + trees.size() + " results");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            onErrorDelegate = error -> {};
        }
    }

    private BatchResult doCompile(Map<URI, Optional<String>> files) {
        // TODO remove all URIs from fileManager
        
        List<JavaFileObject> objects = files
                .entrySet()
                .stream()
                .map(e -> findFile(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        objects.addAll(dependencies(objects));

        JavacTask task = createTask(objects);

        try {
            DiagnosticCollector<JavaFileObject> errors = startCollectingErrors();
            Iterable<? extends CompilationUnitTree> parse = task.parse();

            try {
                Iterable<? extends Element> analyze = task.analyze();

                parse.forEach(tree -> index.update(tree, task));
            } catch (AssertionError e) {
                if (!catchJavacError(e))
                    throw e;
            }

            return new BatchResult(task, parse, errors);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            stopCollectingErrors();
        }
    }

    private boolean catchJavacError(AssertionError e) {
        if (e.getStackTrace().length > 0 && e.getStackTrace()[0].getClassName().startsWith("com.sun.tools.javac")) {
            LOG.log(Level.WARNING, "Failed analyze phase", e);

            return true;
        }
        else return false;
    }

    private Collection<JavaFileObject> dependencies(Collection<JavaFileObject> files) {
        // TODO use index to find dependencies
        return Collections.emptyList();
    }
}