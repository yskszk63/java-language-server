package org.javacs;

import com.google.common.base.Joiner;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Location;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintains a reference to a Java compiler, 
 * and several of its internal data structures,
 * which we need to fiddle with to get incremental compilation 
 * and extract the diagnostic information we want.
 */
public class JavacHolder {
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
     * javac places all of its internal state into this Context object,
     * which is basically a Map<String, Object>
     */
    public Context context;
    /**
     * Error reporting initially goes nowhere.
     * We will replace this with a function that collects errors so we can report all the errors associated with a file at once.
     */
    private DiagnosticListener<JavaFileObject> onErrorDelegate = diagnostic -> {};
    /**
     * javac isn't friendly to swapping out the error-reporting DiagnosticListener,
     * so we install this intermediate DiagnosticListener, which forwards to errorsDelegate
     */
    private final DiagnosticListener<JavaFileObject> onError = diagnostic -> {
        onErrorDelegate.report(diagnostic);
    };

    // Set up SymbolIndex

    /**
     * Index of symbols that gets updated every time you call compile
     */
    public final SymbolIndex index;

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete;

    private static Context reset(Set<Path> classPath,
                                 Set<Path> sourcePath,
                                 Path outputDirectory,
                                 Context existing,
                                 DiagnosticListener<JavaFileObject> onError,
                                 Collection<JavaFileObject> invalidatedSources) {
        Context context = new Context();
        context.put(DiagnosticListener.class, onError);

        // Sets command-line options
        Options options = Options.instance(context);

        options.put("-classpath", Joiner.on(File.pathSeparator).join(classPath));
        options.put("-sourcepath", Joiner.on(File.pathSeparator).join(sourcePath));
        options.put("-d", outputDirectory.toString());
        // You would think we could do -Xlint:all,
        // but some lints trigger fatal errors in the presence of parse errors
        options.put("-Xlint:cast", "");
        options.put("-Xlint:deprecation", "");
        options.put("-Xlint:empty", "");
        options.put("-Xlint:fallthrough", "");
        options.put("-Xlint:finally", "");
        options.put("-Xlint:path", "");
        options.put("-Xlint:unchecked", "");
        options.put("-Xlint:varargs", "");
        options.put("-Xlint:static", "");

        // Pre-register some custom components before javac initializes
        Log.instance(context).multipleErrors = true;

        JavacFileManager fileManager = (JavacFileManager) context.get(JavaFileManager.class);

        if (fileManager == null)
            fileManager = new JavacFileManager(context, true, null);

        ForgivingAttr.instance(context);
        Check.instance(context);
        FuzzyParserFactory.instance(context);

        // Initialize javac
        JavaCompiler compiler = JavaCompiler.instance(context);

        // We're going to use the javadoc comments
        compiler.keepComments = true;

        // This may not be necessary
        Todo.instance(context);
        JavacTrees.instance(context);
        Types.instance(context);

        return context;
    }

    private JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory, Optional<SymbolIndex> index) {
        this.classPath = Collections.unmodifiableSet(classPath);
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.outputDirectory = outputDirectory;
        this.context = reset(classPath, sourcePath, outputDirectory, new Context(), onError, Collections.emptyList());
        this.index = index.orElse(new SymbolIndex());
        this.initialIndexComplete = index.isPresent() ? CompletableFuture.completedFuture(null) : startIndexingSourcePath();

        logStartStopEvents();
        ensureOutputDirectory(outputDirectory);
        clearOutputDirectory(outputDirectory);
    }

    public static JavacHolder create(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        return new JavacHolder(classPath, sourcePath, outputDirectory, Optional.empty());
    }

    public static JavacHolder createWithoutIndex(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        return new JavacHolder(classPath, sourcePath, outputDirectory, Optional.of(new SymbolIndex()));
    }

    private void logStartStopEvents() {
        MultiTaskListener.instance(context).add(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.fine("Started " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.fine("Finished " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();
            }
        });
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
                CompilationResult result = doCompile(files);

                result.trees.forEach(compiled -> {
                    Indexer indexer = new Indexer(index, context);

                    compiled.accept(indexer);
                });

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
     * Set all .class files to modified-at 1970 so javac won't skip them when we invoke it 
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

    private JavaCompiler compiler() {
        return JavaCompiler.instance(context);
    }

    private JavacFileManager fileManager() {
        return (JavacFileManager) context.get(JavaFileManager.class);
    }

    /**
     * Suggest possible completions
     *
     * @param file Path to file
     * @param textContent Current text of file, if available
     * @param cursor Offset in file where the cursor is
     */
    public List<CompletionItem> autocomplete(URI file, Optional<String> textContent, long cursor) {
        initialIndexComplete.join();

        JavaFileObject object = findFile(file, textContent);

        object = TreePruner.putSemicolonAfterCursor(object, file, cursor);

        JCTree.JCCompilationUnit compiled = compileSimple(
                object,
                tree -> new TreePruner(tree, context).removeStatementsAfterCursor(cursor)
        );

        return doAutocomplete(object, compiled, cursor);
    }

    private List<CompletionItem> doAutocomplete(JavaFileObject object, JCTree.JCCompilationUnit pruned, long cursor) {
        AutocompleteVisitor autocompleter = new AutocompleteVisitor(object, cursor, context);

        pruned.accept(autocompleter);

        return autocompleter.suggestions;
    }

    /**
     * Find references to the symbol at the cursor, if there is a symbol at the cursor
     *
     * @param file Path to file
     * @param textContent Current text of file, if available
     * @param cursor Offset in file where the cursor is
     */
    public List<Location> findReferences(URI file, Optional<String> textContent, long cursor) {
        initialIndexComplete.join();

        JCTree.JCCompilationUnit tree = findTree(file, textContent);

        return findSymbol(tree, cursor)
                .map(s -> doFindReferences(s, tree))
                .orElse(Collections.emptyList());
    }

    private List<Location> doFindReferences(Symbol symbol, JCTree.JCCompilationUnit compilationUnit) {
        if (SymbolIndex.shouldIndex(symbol))
            return index.references(symbol).collect(Collectors.toList());
        else {
            return SymbolIndex.nonIndexedReferences(symbol, compilationUnit);
        }
    }

    public Optional<Location> gotoDefinition(URI file, Optional<String> textContent, long cursor) {
        initialIndexComplete.join();

        JCTree.JCCompilationUnit tree = findTree(file, textContent);

        return findSymbol(tree, cursor)
                .flatMap(s -> doGotoDefinition(s, tree));
    }

    private Optional<Location> doGotoDefinition(Symbol symbol, JCTree.JCCompilationUnit compilationUnit) {
        if (SymbolIndex.shouldIndex(symbol))
            return index.findSymbol(symbol).map(info -> info.getLocation());
        else {
            // TODO isn't this ever empty?
            JCTree symbolTree = TreeInfo.declarationFor(symbol, compilationUnit);

            return Optional.of(SymbolIndex.location(symbolTree, compilationUnit));
        }
    }

    private JCTree.JCCompilationUnit findTree(URI file, Optional<String> textContent) {
        return compileSimple(findFile(file, textContent), parsed -> {});
    }

    private Optional<Symbol> findSymbol(JCTree.JCCompilationUnit tree, long cursor) {
        JavaFileObject file = tree.getSourceFile();
        SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file, cursor, context);

        tree.accept(visitor);

        return visitor.found;
    }

    public Optional<Symbol> symbolAt(URI file, Optional<String> textContent, long cursor) {
        initialIndexComplete.join();

        JCTree.JCCompilationUnit tree = findTree(file, textContent);

        return findSymbol(tree, cursor);
    }

    /**
     * File has been deleted
     */
    // TODO delete multiple objects at the same time for performance if user does that
    public CompilationResult delete(URI uri) {
        initialIndexComplete.join();

        JavaFileObject object = findFile(uri, Optional.empty());
        Map<URI, Optional<String>> deps = dependencies(Collections.singleton(object))
                .stream()
                .collect(Collectors.toMap(o -> o.toUri(), o -> Optional.empty()));

        clearObjects(Collections.singleton(object));

        return compile(deps);
    }

    private JavaFileObject findFile(URI file, Optional<String> text) {
        return text
                .map(content -> (JavaFileObject) new StringFileObject(content, file))
                .orElse(fileManager().getRegularFile(new File(file)));
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

    /**
     * Clear files and all their dependents, recompile, compile the index, and report any errors.
     *
     * If these files reference un-compiled dependencies, those dependencies will also be parsed and compiled.
     */
    public CompilationResult compile(Map<URI, Optional<String>> files) {
        initialIndexComplete.join();

        return doCompile(files);
    }

    private CompilationResult doCompile(Map<URI, Optional<String>> files) {
        List<JavaFileObject> objects = files
                .entrySet()
                .stream()
                .map(e -> findFile(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        objects.addAll(dependencies(objects));

        // Clear javac caches
        clearObjects(objects);

        try {
            DiagnosticCollector<JavaFileObject> errors = startCollectingErrors();

            List<JCTree.JCCompilationUnit> parsed = objects.stream()
                    .map(compiler()::parse)
                    .collect(Collectors.toList());

            compileTrees(parsed);

            parsed.forEach(compiled -> {
                Indexer indexer = new Indexer(index, context);

                compiled.accept(indexer);
            });

            return new CompilationResult(parsed, errors);
        } finally {
            stopCollectingErrors();
        }
    }

    private void compileTrees(Collection<JCTree.JCCompilationUnit> parsed) {
        Todo todo = Todo.instance(context);

        compiler().processAnnotations(compiler().enterTrees(com.sun.tools.javac.util.List.from(parsed)));

        while (!todo.isEmpty()) {
            Env<AttrContext> next = todo.remove();

            try {
                // We don't do the desugar or generate phases, because they remove method bodies and methods
                Env<AttrContext> attributedTree = compiler().attribute(next);
                Queue<Env<AttrContext>> analyzedTree = compiler().flow(attributedTree);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error compiling " + next.toplevel.sourcefile.getName(), e);

                // Keep going
            }
        }
    }

    /**
     * Compile without updating dependencies, index, collecting errors.
     * Useful for operations like autocomplete.
     */
    private JCTree.JCCompilationUnit compileSimple(JavaFileObject read, Consumer<JCTree.JCCompilationUnit> afterParse) {
        clearCompilerOnly(Collections.singleton(read));

        JCTree.JCCompilationUnit parse = compiler().parse(read);

        afterParse.accept(parse);
        compileTrees(Collections.singleton(parse));

        return parse;
    }

    private Collection<JavaFileObject> dependencies(Collection<JavaFileObject> files) {
        // TODO
        return Collections.emptyList();
    }


    /**
     * Clear file from javac's caches.
     * This is automatically invoked by other methods of this class; it's exposed only for testing.
     */
    public void clearFiles(Collection<URI> files) {
        List<JavaFileObject> objects = files
                .stream()
                .map(f -> findFile(f, Optional.empty()))
                .collect(Collectors.toList());

        clearObjects(objects);
    }

    /**
     * Clear a file from javac's internal caches
     */
    private void clearObjects(Collection<JavaFileObject> sources) {
        sources.forEach(s -> index.clear(s.toUri()));

        clearCompilerOnly(sources);
    }

    private void clearCompilerOnly(Collection<JavaFileObject> sources) {
        context = reset(classPath, sourcePath, outputDirectory, context, onError, sources);
    }
}