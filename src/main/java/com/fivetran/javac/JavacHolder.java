package com.fivetran.javac;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import javax.tools.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

public class JavacHolder {
    private static final Logger LOG = Logger.getLogger("");
    private final List<String> classPath, sourcePath;
    private final String outputDirectory;
    private final Context context = new Context();
    private DiagnosticListener<JavaFileObject> errorsDelegate = diagnostic -> {};
    private final DiagnosticListener<JavaFileObject> errors = diagnostic -> {
        errorsDelegate.report(diagnostic);
    };

    {
        context.put(DiagnosticListener.class, errors);
    }

    public final JavacFileManager fileManager = new JavacFileManager(context, true, null);

    {
        FuzzyParserFactory.instance(context);
    }

    private final Options options = Options.instance(context);
//    private final IncrementalLog log = new IncrementalLog(context);
    private final JavaCompiler compiler = JavaCompiler.instance(context);
    private final Todo todo = Todo.instance(context);
    private final Map<TaskEvent.Kind, List<BridgeExpressionScanner>> beforeTask = new HashMap<>(), afterTask = new HashMap<>();

    public JavacHolder(List<String> classPath, List<String> sourcePath, String outputDirectory) {
        this.classPath = classPath;
        this.sourcePath = sourcePath;
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(":").join(classPath));
        options.put("-sourcepath", Joiner.on(":").join(sourcePath));
        options.put("-d", outputDirectory);

        MultiTaskListener.instance(context).add(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.info("started " + e);

                List<BridgeExpressionScanner> todo = beforeTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (BridgeExpressionScanner visitor : todo) {
                    visitor.context = context;

                    e.getCompilationUnit().accept(visitor, null);
                }
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.info("finished " + e);

                List<BridgeExpressionScanner> todo = afterTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (BridgeExpressionScanner visitor : todo) {
                    visitor.context = context;

                    e.getCompilationUnit().accept(visitor, null);
                }
            }
        });
    }

    public void afterParse(BridgeExpressionScanner... scan) {
        afterTask.put(TaskEvent.Kind.PARSE, ImmutableList.copyOf(scan));
    }

    public void afterAnalyze(BridgeExpressionScanner... scan) {
        afterTask.put(TaskEvent.Kind.ANALYZE, ImmutableList.copyOf(scan));
    }

    public void onError(DiagnosticListener<JavaFileObject> callback) {
        errorsDelegate = callback;
    }

    public JCTree.JCCompilationUnit parse(JavaFileObject source) {
        clear(source);

        return compiler.parse(source);
    }

    public void check(JCTree.JCCompilationUnit source) {
        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.of(source)));

        while (!todo.isEmpty())
            compiler.generate(compiler.desugar(compiler.flow(compiler.attribute(todo.remove()))));
    }

    private void clear(JavaFileObject source) {
//        log.clear(source);
    }

}
