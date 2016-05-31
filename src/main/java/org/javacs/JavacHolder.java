package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Options;

import javax.tools.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final Set<Path> classPath;
    private final Set<Path> sourcePath;
    private final Path outputDirectory;
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
    private final Options options = Options.instance(context);
    {
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
    // IncrementalLog registers itself in context and pre-empts the normal Log from being created
    private final IncrementalLog log = new IncrementalLog(context);
    public final JavacFileManager fileManager = new JavacFileManager(context, true, null);
    private final ForgivingAttr attr = ForgivingAttr.instance(context);
    private final Check check = Check.instance(context);
    // FuzzyParserFactory registers itself in context and pre-empts the normal ParserFactory from being created
    private final FuzzyParserFactory parserFactory = FuzzyParserFactory.instance(context);
    public final JavaCompiler compiler = JavaCompiler.instance(context);

    {
        compiler.keepComments = true;
    }

    private final Todo todo = Todo.instance(context);
    private final JavacTrees trees = JavacTrees.instance(context);
    // TreeScanner tasks we want to perform before or after compilation stages
    // We'll use these scanners to implement features like go-to-definition
    private final Map<TaskEvent.Kind, List<TreeScanner>> beforeTask = new HashMap<>(), afterTask = new HashMap<>();
    public final ClassIndex index = new ClassIndex(context);

    public JavacHolder(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
        this.classPath = classPath;
        this.sourcePath = sourcePath;
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(":").join(classPath));
        options.put("-sourcepath", Joiner.on(":").join(sourcePath));
        options.put("-d", outputDirectory.toString());

        MultiTaskListener.instance(context).add(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.fine("started " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                List<TreeScanner> todo = beforeTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (TreeScanner visitor : todo) {
                    unit.accept(visitor);
                }
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.fine("finished " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                if (e.getKind() == TaskEvent.Kind.ANALYZE)
                    unit.accept(index);

                List<TreeScanner> todo = afterTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (TreeScanner visitor : todo) {
                    unit.accept(visitor);
                }
            }
        });

        ensureOutputDirectory(outputDirectory);
        clearOutputDirectory(outputDirectory);
    }

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
     * After the parse phase of compilation,
     * scan the source trees with these scanners.
     * Replaces any existing after-parse scanners.
     */
    public void afterParse(TreeScanner... scan) {
        afterTask.put(TaskEvent.Kind.PARSE, ImmutableList.copyOf(scan));
    }

    /**
     * After the analysis phase of compilation,
     * scan the source trees with these scanners.
     * Replaces any existing after-analyze scanners.
     */
    public void afterAnalyze(TreeScanner... scan) {
        afterTask.put(TaskEvent.Kind.ANALYZE, ImmutableList.copyOf(scan));
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

    public void compile(Collection<JCTree.JCCompilationUnit> parsed) {
        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.from(parsed)));

        while (!todo.isEmpty()) {
            // We don't do the desugar or generate phases, because they remove method bodies and methods
            Env<AttrContext> next = todo.remove();
            Env<AttrContext> attributedTree = compiler.attribute(next);
            Queue<Env<AttrContext>> analyzedTree = compiler.flow(attributedTree);
        }
    }
    
    /**
     * Compile a source tree produced by this.parse
     */
    // TODO inline
    public void compile(JCTree.JCCompilationUnit source) {
        compile(Collections.singleton(source));
    }

    /**
     * Remove source file from caches in the parse stage
     */
    private void clear(JavaFileObject source) {
        // Forget about this file
        log.clear(source);

        // javac's flow stage will stop early if there are errors
        log.nerrors = 0;
        log.nwarnings = 0;

        // Remove all cached classes that came from this files
        List<Name> remove = new ArrayList<>();

        check.compiled.forEach((name, symbol) -> {
            if (symbol.sourcefile.getName().equals(source.getName()))
                remove.add(name);
        });

        remove.forEach(check.compiled::remove);
    }
}
