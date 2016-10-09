package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.*;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Field;
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

/**
 * Maintains a reference to a Java compiler, 
 * and several of its internal data structures,
 * which we need to fiddle with to get incremental compilation 
 * and extract the diagnostic information we want.
 */
public class JavacHolder {
    private static final Logger LOG = Logger.getLogger("main");
    /** Where this javac looks for library .class files */
    public final Set<Path> classPath;
    /** Where this javac looks for .java source files */
    public final Set<Path> sourcePath;
    /** Where this javac places generated .class files */
    public final Path outputDirectory;
    // javac places all of its internal state into this Context object,
    // which is basically a Map<String, Object>
    public final Context context = new Context();
    // Error reporting initially goes nowhere
    // When we want to report errors back to VS Code, we'll replace this with something else
    private DiagnosticListener<JavaFileObject> errorsDelegate = diagnostic -> {};
    // javac isn't friendly to swapping out the error-reporting DiagnosticListener,
    // so we install this intermediate DiagnosticListener, which forwards to errorsDelegate
    private final DiagnosticListener<JavaFileObject> errors = diagnostic -> {
        errorsDelegate.report(diagnostic);
    };
    
    {
        context.put(DiagnosticListener.class, errors);
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

    public JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        this.classPath = Collections.unmodifiableSet(classPath);
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(File.pathSeparator).join(classPath));
        options.put("-sourcepath", Joiner.on(File.pathSeparator).join(sourcePath));
        options.put("-d", outputDirectory.toString());

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

        ensureOutputDirectory(outputDirectory);
        clearOutputDirectory(outputDirectory);
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
     * Send all errors to callback, replacing any existing callback
     */
    public void onError(DiagnosticListener<JavaFileObject> callback) {
        errorsDelegate = callback;
    }

    /**
     * Compile the indicated source file, and its dependencies if they have been modified.
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
    public void compile(Collection<JCTree.JCCompilationUnit> parsed) {
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
     * Clear a file from javac's internal caches
     */
    public void clear(JavaFileObject source) {
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
