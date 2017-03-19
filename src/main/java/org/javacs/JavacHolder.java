package org.javacs;

import com.google.common.base.Joiner;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Location;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.List;
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
    public final Context context = new Context();
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
    
    {
        context.put(DiagnosticListener.class, onError);
    }
    
    // Sets command-line options
    private final Options options = Options.instance(context);
    
    {
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
    }

    // Pre-register some custom components before javac initializes
    
    private final Log log = Log.instance(context);

    {
        log.multipleErrors = true;
    }

    public final JavacFileManager fileManager = new JavacFileManager(context, true, null);
    private final ForgivingAttr attr = ForgivingAttr.instance(context);
    private final Check check = Check.instance(context);
    private final FuzzyParserFactory parserFactory = FuzzyParserFactory.instance(context);
    
    // Initialize javac

    public final JavaCompiler compiler = JavaCompiler.instance(context);

    {
        // We're going to use the javadoc comments
        compiler.keepComments = true;
    }

    // javac has already been initialized, fetch a few components for easy access

    private final Todo todo = Todo.instance(context);
    private final JavacTrees trees = JavacTrees.instance(context);
    private final Types types = Types.instance(context);

    // Set up SymbolIndex

    /**
     * Index of symbols that gets updated every time you call update
     */
    public final SymbolIndex index = new SymbolIndex(this);

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete;

    public JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        this.classPath = Collections.unmodifiableSet(classPath);
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(File.pathSeparator).join(classPath));
        options.put("-sourcepath", Joiner.on(File.pathSeparator).join(sourcePath));
        options.put("-d", outputDirectory.toString());

        logStartStopEvents();
        ensureOutputDirectory(outputDirectory);
        clearOutputDirectory(outputDirectory);

        initialIndexComplete = startIndexingSourcePath();
    }

    private void logStartStopEvents() {
        MultiTaskListener.instance(context).add(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.fine("started " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.fine("finished " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();
            }
        });
    }

    /**
     * Index exported declarations and references for all files on the source path
     * This may take a while, so we'll do it on an extra thread
     */
    private CompletableFuture<Void> startIndexingSourcePath() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Thread worker = new Thread("InitialIndex") {
            List<JCTree.JCCompilationUnit> parsed = new ArrayList<>();
            List<Path> paths = new ArrayList<>();

            @Override
            public void run() {
                // Parse each file
                sourcePath.forEach(s -> parseAll(s, parsed, paths));

                // Compile all parsed files
                compile(parsed);

                parsed.forEach(index::update);

                // TODO minimize memory use during this process
                // Instead of doing parse-all / compile-all,
                // queue all files, then do parse / compile on each
                // If invoked correctly, javac should avoid reparsing the same file twice
                // Then, use the same mechanism as the desugar / generate phases to remove method bodies,
                // to reclaim memory as we go

                done.complete(null);

                // TODO verify that compiler and all its resources get destroyed
            }

            /**
             * Look for .java files and invalidate them
             */
            private void parseAll(Path path, List<JCTree.JCCompilationUnit> trees, List<Path> paths) {
                if (Files.isDirectory(path)) try {
                    Files.list(path).forEach(p -> parseAll(p, trees, paths));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                else if (path.getFileName().toString().endsWith(".java")) {
                    LOG.info("Index " + path);

                    JavaFileObject file = fileManager.getRegularFile(path.toFile());

                    trees.add(parse(file));
                    paths.add(path);
                }
            }
        };

        worker.start();

        return done;
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

    /**
     * Suggest possible completions
     *
     * @param file Path to file
     * @param textContent Current text of file, if available
     * @param cursor Offset in file where the cursor is
     */
    public List<CompletionItem> autocomplete(URI file, Optional<String> textContent, long cursor) {
        JavaFileObject object = findFile(file, textContent);

        object = TreePruner.putSemicolonAfterCursor(object, file, cursor);

        JCTree.JCCompilationUnit tree = parse(object);

        // Remove all statements after the cursor
        // There are often parse errors after the cursor, which can generate unrecoverable type errors
        new TreePruner(tree, context).removeStatementsAfterCursor(cursor);

        compile(Collections.singleton(tree));

        return doAutocomplete(object, tree, cursor);
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
        JCTree.JCCompilationUnit tree = parse(findFile(file, textContent));

        compile(Collections.singleton(tree));

        index.update(tree, context);

        return tree;
    }

    private Optional<Symbol> findSymbol(JCTree.JCCompilationUnit tree, long cursor) {
        JavaFileObject file = tree.getSourceFile();
        SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file, cursor, context);

        tree.accept(visitor);

        return visitor.found;
    }

    public Optional<Symbol> symbolAt(URI file, Optional<String> textContent, long cursor) {
        JCTree.JCCompilationUnit tree = findTree(file, textContent);

        return findSymbol(tree, cursor);
    }

    /**
     * Clear files and all their dependents, recompile, update the index, and report any errors.
     */
    public DiagnosticCollector<JavaFileObject> update(Map<URI, Optional<String>> files) {
        List<JavaFileObject> objects = files
                .entrySet()
                .stream()
                .map(e -> findFile(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return doUpdate(objects);
    }

    /**
     * Exposed for testing only!
     */
    public DiagnosticCollector<JavaFileObject> doUpdate(Collection<JavaFileObject> objects) {
        List<JCTree.JCCompilationUnit> parsed = objects
                .stream()
                .map(f -> {
                    clear(f);

                    return f;
                })
                .map(this::parse)
                .collect(Collectors.toList());

        // TODO add all dependents

        return compile(parsed);
    }

    /**
     * File has been deleted
     */
    public void delete(URI uri) {
        // TODO
    }

    private JavaFileObject findFile(URI file, Optional<String> text) {
        return text
                .map(content -> (JavaFileObject) new StringFileObject(content, file))
                .orElse(fileManager.getRegularFile(new File(file)));
    }

    /**
     * Parse the indicated source file, and its dependencies if they have been modified.
     */
    public JCTree.JCCompilationUnit parse(JavaFileObject source) {
        clear(source);

        JCTree.JCCompilationUnit result = compiler.parse(source);

        return result;
    }

    /**
     * Compile a set of parsed files.
     * 
     * If these files reference un-parsed dependencies, those dependencies will also be parsed and compiled.
     */
    public DiagnosticCollector<JavaFileObject> compile(Collection<JCTree.JCCompilationUnit> parsed) {
        try {
            DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();

            onErrorDelegate = error -> {
                if (error.getStartPosition() != Diagnostic.NOPOS)
                    errors.report(error);
            };

            compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.from(parsed)));

            while (!todo.isEmpty()) {
                Env<AttrContext> next = todo.remove();

                try {
                    // We don't do the desugar or generate phases, because they remove method bodies and methods
                    Env<AttrContext> attributedTree = compiler.attribute(next);
                    Queue<Env<AttrContext>> analyzedTree = compiler.flow(attributedTree);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error compiling " + next.toplevel.sourcefile.getName(), e);

                    // Keep going
                }
            }

            return errors;
        } finally {
            onErrorDelegate = error -> {};
        }
    }

    /**
     * Clear a file from javac's internal caches
     */
    private void clear(JavaFileObject source) {
        // TODO clear dependencies as well (dependencies should get stored in SymbolIndex)

        // Forget about this file
        Consumer<JavaFileObject> removeFromLog = logRemover(log);

        removeFromLog.accept(source);

        // javac's flow stage will stop early if there are errors
        log.nerrors = 0;
        log.nwarnings = 0;

        // Remove all cached classes that came from this files
        List<Name> remove = new ArrayList<>();

        Consumer<Type> removeFromClosureCache = closureCacheRemover(types);

        check.compiled.forEach((name, symbol) -> {
            if (symbol.sourcefile.getName().equals(source.getName()))
                remove.add(name);

            removeFromClosureCache.accept(symbol.type);
        });

        remove.forEach(check.compiled::remove);

    }

    /** 
     * Reflectively invokes Types.closureCache.remove(Type) 
     */
    private static Consumer<Type> closureCacheRemover(Types types) {
        try {
            Field closureCache = Types.class.getDeclaredField("closureCache");

            closureCache.setAccessible(true);

            Map<Type, com.sun.tools.javac.util.List<Type>> value = (Map<Type, com.sun.tools.javac.util.List<Type>>) closureCache.get(types);

            return value::remove;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /** 
     * Reflectively invokes Log.sourceMap.remove(JavaFileObject) 
     */
    private static Consumer<JavaFileObject> logRemover(Log log) {
        try {
            Field sourceMap = AbstractLog.class.getDeclaredField("sourceMap");

            sourceMap.setAccessible(true);

            Map<JavaFileObject, DiagnosticSource> value = (Map<JavaFileObject, DiagnosticSource>) sourceMap.get(log);

            return value::remove;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}