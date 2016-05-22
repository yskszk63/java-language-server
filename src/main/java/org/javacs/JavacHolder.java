package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Todo;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavacHolder {
    private static final Logger LOG = Logger.getLogger("main");
    private final List<Path> classPath;
    private final List<Path> sourcePath;
    private final Path outputDirectory;
    public final Context context = new Context();
    private DiagnosticListener<JavaFileObject> errorsDelegate = diagnostic -> {};
    private final DiagnosticListener<JavaFileObject> errors = diagnostic -> {
        errorsDelegate.report(diagnostic);
    };

    {
        context.put(DiagnosticListener.class, errors);
    }

    private final IncrementalLog log = new IncrementalLog(context);
    public final JavacFileManager fileManager = new JavacFileManager(context, true, null);
    private final Check check = Check.instance(context);
    private final FuzzyParserFactory parserFactory = FuzzyParserFactory.instance(context);
    private final Options options = Options.instance(context);
    private final JavaCompiler compiler = JavaCompiler.instance(context);
    private final Todo todo = Todo.instance(context);
    private final JavacTrees trees = JavacTrees.instance(context);
    private final Map<TaskEvent.Kind, List<TreeScanner>> beforeTask = new HashMap<>(), afterTask = new HashMap<>();
    private final ClassIndex index = new ClassIndex(context);

    public JavacHolder(List<Path> classPath, List<Path> sourcePath, Path outputDirectory) {
        this.classPath = classPath;
        this.sourcePath = sourcePath;
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(":").join(classPath));
        options.put("-sourcepath", Joiner.on(":").join(sourcePath));
        options.put("-d", outputDirectory.toString());

        MultiTaskListener.instance(context).add(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.info("started " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                List<TreeScanner> todo = beforeTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (TreeScanner visitor : todo) {
                    unit.accept(visitor);
                }
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.info("finished " + e);

                JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                if (e.getKind() == TaskEvent.Kind.ANALYZE)
                    unit.accept(index);

                List<TreeScanner> todo = afterTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (TreeScanner visitor : todo) {
                    unit.accept(visitor);
                }
            }
        });

        clearOutputDirectory(outputDirectory);
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

    public void afterParse(TreeScanner... scan) {
        afterTask.put(TaskEvent.Kind.PARSE, ImmutableList.copyOf(scan));
    }

    public void afterAnalyze(TreeScanner... scan) {
        afterTask.put(TaskEvent.Kind.ANALYZE, ImmutableList.copyOf(scan));
    }

    public void onError(DiagnosticListener<JavaFileObject> callback) {
        errorsDelegate = callback;
    }

    public JCTree.JCCompilationUnit parse(JavaFileObject source) {
        StringJoiner command = new StringJoiner(" ");
        
        command.add("javac");
        
        for (String key : options.keySet()) {
            String value = options.get(key);
            
            command.add(key);
            command.add(value);
        }
        
        if (source instanceof SimpleJavaFileObject) {
            SimpleJavaFileObject simple = (SimpleJavaFileObject) source;
            
            command.add(simple.toUri().getPath());
        }
        
        LOG.info(command.toString());
        
        clear(source);

        JCTree.JCCompilationUnit result = compiler.parse(source);

        // Search for class definitions in this file
        // Remove them from downstream caches so that we can re-compile this class
        result.accept(new TreeScanner() {
            @Override
            public void visitClassDef(JCTree.JCClassDecl that) {
                super.visitClassDef(that);

                clear(that.name);
            }
        });

        return result;
    }

    public void compile(JCTree.JCCompilationUnit source) {
        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.of(source)));

        while (!todo.isEmpty()) {
            // We don't do the desugar or generate phases, because they remove method bodies and methods
            Env<AttrContext> next = todo.remove();
            Env<AttrContext> attributedTree = compiler.attribute(next);
            Queue<Env<AttrContext>> analyzedTree = compiler.flow(attributedTree);
        }
    }

    /**
     * Remove source file from caches in the parse stage
     */
    private void clear(JavaFileObject source) {
        log.clear(source);
    }

    /**
     * Remove class definitions from caches in the compile stage
     */
    private void clear(Name name) {
        check.compiled.remove(name);
    }

    private CompilationUnitTree compilationUnit(Symbol symbol) {
        return trees.getPath(symbol).getCompilationUnit();
    }
}
