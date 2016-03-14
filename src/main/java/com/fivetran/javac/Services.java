package com.fivetran.javac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fivetran.javac.autocomplete.AutocompleteVisitor;
import com.fivetran.javac.message.*;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.comp.CompileStates;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class Services {
    private static final Logger LOG = Logger.getLogger("");

    public ResponseAutocomplete autocomplete(RequestAutocomplete request) throws IOException {
        Path path = Paths.get(request.path);
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        StringFileObject file = new StringFileObject(request.text, path);
        LineMap lines = LineMap.fromString(request.text);
        long cursor = lines.offset(request.row, request.column);
        AutocompleteVisitor autocompleter = new AutocompleteVisitor(cursor);
        JavacTask task = JavacTaskBuilder.create()
                                         .fuzzyParser()
                                         .addFile(file)
                                         .reportErrors(errors)
                                         .afterAnalyze(autocompleter)
                                         .classPath(request.config.classPath)
                                         .sourcePath(request.config.sourcePath)
                                         .outputDirectory(request.config.outputDirectory.orElse("target"))
                                         // TODO maven dependencies
                                         .stopIfError(CompileStates.CompileState.GENERATE)
                                         .build();

        task.call();

        for (Diagnostic<? extends JavaFileObject> error : errors.getDiagnostics()) {
            LOG.warning(error.toString());
        }

        return new ResponseAutocomplete(autocompleter.suggestions);
    }

    public JsonNode echo(JsonNode echo) {
        return echo;
    }

    public ResponseLint lint(RequestLint request) throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        Path path = Paths.get(request.path);
        JavaFileObject file = JavacTaskBuilder.STANDARD_FILE_MANAGER.getRegularFile(path.toFile());
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(file)
                                         .reportErrors(errors)
                                         .classPath(request.config.classPath)
                                         .sourcePath(request.config.sourcePath)
                                         .outputDirectory(request.config.outputDirectory.orElse("target"))
                                         // TODO maven dependencies
                                         .build();

        task.call();

        ResponseLint response = new ResponseLint();

        for (Diagnostic<? extends JavaFileObject> error : errors.getDiagnostics()) {
            Range range = position(error);
            String lintPath = error.getSource().toUri().getPath();
            LintMessage message = new LintMessage(lintPath,
                                                  range,
                                                  error.getMessage(null),
                                                  LintMessage.Type.Error);

            response.messages.add(message);
        }

        return response;
    }

    private Range position(Diagnostic<? extends JavaFileObject> error) {
        if (error.getStartPosition() == Diagnostic.NOPOS)
            return Range.NONE;

        Position start = new Position(error.getLineNumber() - 1, error.getColumnNumber() - 1);
        Position end = endPosition(error);

        return new Range(start, end);
    }

    private Position endPosition(Diagnostic<? extends JavaFileObject> error) {
        try {
            Reader reader = error.getSource().openReader(true);
            long startOffset = error.getStartPosition();
            long endOffset = error.getEndPosition();

            reader.skip(startOffset);

            long line = error.getLineNumber() - 1;
            long column = error.getColumnNumber() - 1;

            for (long i = startOffset; i < endOffset; i++) {
                int next = reader.read();

                if (next == '\n') {
                    line++;
                    column = 0;
                }
                else
                    column++;
            }

            return new Position(line, column);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
