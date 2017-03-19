package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
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

    private final JavacFileManager fileManager = new JavacFileManager(context, true, null);
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
     * Index of symbols that gets updated every time you call compile
     */
    public final SymbolIndex index = new SymbolIndex(this);

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete;

    public JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        this(classPath, sourcePath, outputDirectory, true);
    }

    public JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory, boolean index) {
        this.classPath = Collections.unmodifiableSet(classPath);
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(File.pathSeparator).join(classPath));
        options.put("-sourcepath", Joiner.on(File.pathSeparator).join(sourcePath));
        options.put("-d", outputDirectory.toString());

        logStartStopEvents();
        ensureOutputDirectory(outputDirectory);
        clearOutputDirectory(outputDirectory);

        if (index)
            initialIndexComplete = startIndexingSourcePath();
        else
            initialIndexComplete = CompletableFuture.completedFuture(null);
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
            @Override
            public void run() {
                List<URI> objects = new ArrayList<>();

                // Parse each file
                sourcePath.forEach(s -> findAllFiles(s, objects));

                // Compile all parsed files
                Map<URI, Optional<String>> files = objects.stream().collect(Collectors.toMap(key -> key, key -> Optional.empty()));
                CompilationResult result = doCompile(files);

                result.trees.forEach(index::update);

                // TODO minimize memory use during this process
                // Instead of doing parse-all / compileFileObjects-all,
                // queue all files, then do parse / compileFileObjects on each
                // If invoked correctly, javac should avoid reparsing the same file twice
                // Then, use the same mechanism as the desugar / generate phases to remove method bodies,
                // to reclaim memory as we go

                done.complete(null);

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
    public CompilationResult delete(URI uri) {
        initialIndexComplete.join();

        JavaFileObject object = findFile(uri, Optional.empty());
        Map<URI, Optional<String>> deps = dependencies(Collections.singleton(object))
                .stream()
                .collect(Collectors.toMap(o -> o.toUri(), o -> Optional.empty()));

        clear(object);

        return compile(deps);
    }

    private JavaFileObject findFile(URI file, Optional<String> text) {
        return text
                .map(content -> (JavaFileObject) new StringFileObject(content, file))
                .orElse(fileManager.getRegularFile(new File(file)));
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
        objects.forEach(this::clear);

        try {
            DiagnosticCollector<JavaFileObject> errors = startCollectingErrors();

            List<JCTree.JCCompilationUnit> parsed = objects.stream()
                    .map(compiler::parse)
                    .collect(Collectors.toList());

            compileTrees(parsed);

            parsed.forEach(index::update);

            return new CompilationResult(parsed, errors);
        } finally {
            stopCollectingErrors();
        }
    }

    private void compileTrees(Collection<JCTree.JCCompilationUnit> parsed) {
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
    }

    /**
     * Compile without updating dependencies, index, collecting errors.
     * Useful for operations like autocomplete.
     */
    private JCTree.JCCompilationUnit compileSimple(JavaFileObject read, Consumer<JCTree.JCCompilationUnit> afterParse) {
        clear(read);

        JCTree.JCCompilationUnit parse = compiler.parse(read);

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
    public void clear(URI file) {
        clear(findFile(file, Optional.empty()));
    }

    /**
     * Clear a file from javac's internal caches
     */
    private void clear(JavaFileObject source) {
        index.clear(source.toUri());

        // Forget about this file
        Consumer<JavaFileObject> removeFromLog = logRemover(log);

        removeFromLog.accept(source);

        // javac's flow stage will stop early if there are errors
        log.nerrors = 0;
        log.nwarnings = 0;

        // Identify all classes that are in this file
        Map<Name, Symbol.ClassSymbol> enclosed = new HashMap<>(Maps.filterValues(
                check.compiled,
                symbol -> symbol != null && symbol.sourcefile.getName().equals(source.getName())
        ));

        // Clear javac caches
        Consumer<Type> removeFromClosureCache = closureCacheRemover(types);
        Symtab symtab = Symtab.instance(context);
        Enter enter = Enter.instance(context);
        Consumer<Symbol> removeFromTypeEnvs = typeEnvsRemover(enter);

        for (Name name : enclosed.keySet()) {
            // Remove from class-name => class-symbol map
            check.compiled.remove(name);
            // Remove from class-name => package-symbol map
            symtab.packages.remove(name);
            // Remove from class-name => class-symbol map
            symtab.classes.remove(name);

        }

        for (Symbol.ClassSymbol symbol : enclosed.values()) {
            // Remove from type => supertypes map
            removeFromClosureCache.accept(symbol.type);
            // Remove from type-symbol -> Env map
            removeFromTypeEnvs.accept(symbol);
        }

        CompileStates compileStates = CompileStates.instance(context);

        compileStates.entrySet().removeIf(entry -> {
            Env<AttrContext> env = entry.getKey();

            return containsClass(env, enclosed.values());
        });
    }

    private boolean containsClass(Env<AttrContext> env, Collection<? extends Symbol> symbols) {
        if (env == null)
            return false;

        for (Symbol symbol : env.info.getLocalElements()) {
            if (symbols.contains(symbol))
                return true;
        }

        return containsClass(env.outer, symbols);
    }

    /**
     * Reflectively invokes Enter.typeEnvs.remove(Symbol)
     */
    private static Consumer<Symbol> typeEnvsRemover(Enter enter) {
        try {
            Field typeEnvs = Enter.class.getDeclaredField("typeEnvs");

            typeEnvs.setAccessible(true);

            Map<Symbol.TypeSymbol,Env<AttrContext>> value = (Map<Symbol.TypeSymbol,Env<AttrContext>>) typeEnvs.get(enter);

            return value::remove;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
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