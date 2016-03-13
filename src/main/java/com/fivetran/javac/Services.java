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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
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
                                         .classPath(request.classPath)
                                         .sourcePath(request.sourcePath)
                                         .outputDirectory(request.outputDirectory.orElse("target"))
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
        LineMap lines = LineMap.fromPath(path);
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(file)
                                         .reportErrors(errors)
                                         .classPath(request.classPath)
                                         .sourcePath(request.sourcePath)
                                         .outputDirectory(request.outputDirectory.orElse("target"))
                                         .build();

        task.call();

        ResponseLint response = new ResponseLint();

        for (Diagnostic<? extends JavaFileObject> error : errors.getDiagnostics()) {
            if (error.getStartPosition() == Diagnostic.NOPOS) 
                LOG.warning("Error " + error.getMessage(null) + " has no location");
            else {
                Position start = lines.point(error.getStartPosition());
                Position end = lines.point(error.getEndPosition());
                Range range = new Range(start, end);
                LintMessage message = new LintMessage(error.getSource().toUri().getPath(),
                                                      range,
                                                      error.getMessage(null),
                                                      LintMessage.Type.Error);

                response.messages.add(message);
            }
        }

        return response;
    }
}
