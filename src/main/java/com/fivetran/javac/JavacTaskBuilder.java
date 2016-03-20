package com.fivetran.javac;

import com.google.common.base.Joiner;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.util.Context;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class JavacTaskBuilder {
    private static final Logger LOG = Logger.getLogger("");

    public static final JavacTool SYSTEM_JAVA_COMPILER = (JavacTool) ToolProvider.getSystemJavaCompiler();
    public static final JavacFileManager STANDARD_FILE_MANAGER = SYSTEM_JAVA_COMPILER.getStandardFileManager(null, null, null);

    private final Context context;
    private boolean fuzzyParser = false;
    /** Files we're going to compile */
    private final List<JavaFileObject> files = new ArrayList<>();
    /** Error collector. Can only be set once. */
    private DiagnosticCollector<JavaFileObject> errors;
    /** Tasks that get run after the parsing phase of compilation */
    private List<BridgeExpressionScanner> afterParse = new ArrayList<>();
    /** Tasks that get run after the enter phase of compilation */
    private List<BridgeExpressionScanner> afterEnter = new ArrayList<>();
    /** Tasks that get run after the analysis phase of compilation */
    private List<BridgeExpressionScanner> afterAnalyze = new ArrayList<>();
    /** Command line options */
    private List<String> options = new ArrayList<>();
    /** When to stop if error */
    private CompileStates.CompileState shouldStopPolicyIfError = CompileStates.CompileState.ENTER;
    /** When to stop if no error */
    private CompileStates.CompileState shouldStopPolicyIfNoError = CompileStates.CompileState.GENERATE;

    private JavacTaskBuilder(Context context) {
        this.context = context;
    }

    /**
     * Build a JavacTask using the system java compiler and the standard file manager
     */
    public static JavacTaskBuilder create() {
        return createWithContext(new Context());
    }

    public static JavacTaskBuilder createWithContext(Context context) {
        return new JavacTaskBuilder(context);
    }

    public JavacTaskBuilder fuzzyParser() {
        fuzzyParser = true;

        return this;
    }

    /**
     * Add a file to the compilation todo list
     */
    public JavacTaskBuilder addFile(JavaFileObject file) {
        files.add(file);

        return this;
    }

    /**
     * Report errors from all phases of compilation to collector
     */
    public JavacTaskBuilder reportErrors(DiagnosticCollector<JavaFileObject> collector) {
        if (errors != null)
            throw new IllegalStateException();
        else {
            errors = collector;

            return this;
        }
    }

    /**
     * After parsing, scan the abstract syntax tree with visitor.
     * The javac parser has error recovery, so this will still work even if there are syntax errors.
     */
    public JavacTaskBuilder afterParse(BridgeExpressionScanner visitor) {
        afterParse.add(visitor);

        return this;
    }

    /**
     * After entering the tree, scan the abstract syntax tree with visitor.
     * If syntax errors are present, visitor will never get run.
     */
    public JavacTaskBuilder afterEnter(BridgeExpressionScanner visitor) {
        afterEnter.add(visitor);

        return this;
    }

    /**
     * After analysis, scan the abstract syntax tree with visitor.
     * If syntax errors are present, visitor will never get run.
     */
    public JavacTaskBuilder afterAnalyze(BridgeExpressionScanner visitor) {
        afterAnalyze.add(visitor);

        return this;
    }

    public JavacTaskBuilder classPath(List<String> classPath) {
        LOG.info("classpath: " + classPath);

        options.add("-classpath");
        options.add(Joiner.on(':').join(classPath));

        return this;
    }

    public JavacTaskBuilder sourcePath(List<String> sourcePath) {
        LOG.info("sourcepath: " + sourcePath);

        options.add("-sourcepath");
        options.add(Joiner.on(':').join(sourcePath));

        return this;
    }

    public JavacTaskBuilder outputDirectory(String outputDirectory) {
        LOG.info("outputDirectory: " + outputDirectory);

        try {
            Files.createDirectories(Paths.get(outputDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        options.add("-d");
        options.add(outputDirectory);

        return this;
    }

    public JavacTaskBuilder stopIfError(CompileStates.CompileState state) {
        shouldStopPolicyIfError = state;

        return this;
    }

    public JavacTaskBuilder stopIfNoError(CompileStates.CompileState state) {
        shouldStopPolicyIfNoError = state;

        return this;
    }

    public JavacTask build() {
        JavacTask task = SYSTEM_JAVA_COMPILER.getTask(null,
                                                      STANDARD_FILE_MANAGER,
                                                      errors,
                                                      options,
                                                      null,
                                                      files,
                                                      context);

        if (fuzzyParser)
            FuzzyParserFactory.instance(context);

        JavaCompiler.instance(context).shouldStopPolicyIfError = shouldStopPolicyIfError;
        JavaCompiler.instance(context).shouldStopPolicyIfNoError = shouldStopPolicyIfNoError;

        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.info("started " + e);
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.info("finished " + e);

                switch (e.getKind()) {
                    case PARSE:
                        for (BridgeExpressionScanner visitor : afterParse) {
                            visitor.task = task;

                            e.getCompilationUnit().accept(visitor, null);
                        }

                        break;
                    case ENTER:
                        for (BridgeExpressionScanner visitor : afterEnter) {
                            visitor.task = task;

                            e.getCompilationUnit().accept(visitor, null);
                        }

                        break;
                    case ANALYZE:
                        for (BridgeExpressionScanner visitor : afterAnalyze) {
                            visitor.task = task;

                            e.getCompilationUnit().accept(visitor, null);
                        }

                        break;
                    case GENERATE:
                        break;
                    case ANNOTATION_PROCESSING:
                        break;
                    case ANNOTATION_PROCESSING_ROUND:
                        break;
                }
            }
        });

        return task;
    }
}
