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
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class JavacProvider {
    private static final Logger LOG = Logger.getLogger("");
    public static final JavacTool SYSTEM_JAVA_COMPILER = (JavacTool) ToolProvider.getSystemJavaCompiler();
    public static final JavacFileManager STANDARD_FILE_MANAGER = SYSTEM_JAVA_COMPILER.getStandardFileManager(null, null, null);

    public static Context compiler(List<String> classPath,
                                   List<String> sourcePath,
                                   boolean fuzzyParser,
                                   String outputDirectory,
                                   DiagnosticListener<JavaFileObject> errors,
                                   BridgeExpressionScanner afterParse,
                                   BridgeExpressionScanner afterEnter,
                                   BridgeExpressionScanner afterAnalyze) {
        Context context = new Context();
        List<String> options = new ArrayList<>();

        options.add("-classpath");
        options.add(Joiner.on(':').join(classPath));

        options.add("-sourcepath");
        options.add(Joiner.on(':').join(sourcePath));

        try {
            Files.createDirectories(Paths.get(outputDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        options.add("-d");
        options.add(outputDirectory);

        JavacTask task = SYSTEM_JAVA_COMPILER.getTask(null,
                                                      STANDARD_FILE_MANAGER,
                                                      errors,
                                                      options,
                                                      null,
                                                      Collections.emptyList(),
                                                      context);

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
                        afterParse.task = task;

                        e.getCompilationUnit().accept(afterParse, null);

                        break;
                    case ENTER:
                        afterEnter.task = task;

                        e.getCompilationUnit().accept(afterEnter, null);

                        break;
                    case ANALYZE:
                        afterAnalyze.task = task;

                        e.getCompilationUnit().accept(afterAnalyze, null);

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

        if (fuzzyParser)
            FuzzyParserFactory.instance(context);

        return context;
    }
}
