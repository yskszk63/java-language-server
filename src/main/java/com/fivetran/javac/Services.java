package com.fivetran.javac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fivetran.javac.message.AutocompleteVisitor;
import com.fivetran.javac.message.*;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class Services {
    private static final Logger LOG = Logger.getLogger("");

    public ResponseAutocomplete autocomplete(RequestAutocomplete request) throws IOException {
        Path path = Paths.get(request.path);
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        StringFileObject file = new StringFileObject(request.text, path);
        LineMap lines = LineMap.fromString(request.text);
        long cursor = lines.offset(request.position.line, request.position.character);
        AutocompleteVisitor autocompleter = new AutocompleteVisitor(cursor);
        JavacHolder compiler = new JavacHolder(classPath(request.config),
                                               request.config.sourcePath,
                                               request.config.outputDirectory.orElse("target"));

        compiler.afterAnalyze(autocompleter);
        compiler.onError(errors);
        compiler.compile(compiler.parse(file));

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
        JavacHolder compiler = new JavacHolder(classPath(request.config),
                                               request.config.sourcePath,
                                               request.config.outputDirectory.orElse("target"));
        JavaFileObject file = compiler.fileManager.getRegularFile(path.toFile());
        compiler.onError(errors);

        compiler.compile(compiler.parse(file));

        ResponseLint response = new ResponseLint();

        for (Diagnostic<? extends JavaFileObject> error : errors.getDiagnostics()) {
            Range range = position(error);
            String lintPath = error.getSource().toUri().getPath();
            LintMessage message = new LintMessage(range,
                                                  error.getMessage(null),
                                                  LintMessage.Type.Error);
            List<LintMessage> ms = response.messages.computeIfAbsent(lintPath, newPath -> new ArrayList<>());

            ms.add(message);
        }

        return response;
    }

    private List<String> classPath(JavaConfig config) {
        List<String> acc = new ArrayList<>();

        acc.addAll(config.classPath);

        if (config.classPathFile.isPresent()) {
            try (BufferedReader reader = Files.newBufferedReader(Paths.get(config.classPathFile.get()))) {
                reader.lines()
                      .flatMap(line -> Arrays.stream(line.split(":")))
                      .forEach(acc::add);
            } catch (IOException e) {
                throw new ReturnError("Error reading classPathFile " + config.classPathFile, e);
            }
        }

        return acc;
    }

    private Range position(Diagnostic<? extends JavaFileObject> error) {
        if (error.getStartPosition() == Diagnostic.NOPOS)
            return Range.NONE;

        Position start = new Position((int) error.getLineNumber() - 1, (int) error.getColumnNumber() - 1);
        Position end = endPosition(error);

        return new Range(start, end);
    }

    private Position endPosition(Diagnostic<? extends JavaFileObject> error) {
        try {
            Reader reader = error.getSource().openReader(true);
            long startOffset = error.getStartPosition();
            long endOffset = error.getEndPosition();

            reader.skip(startOffset);

            int line = (int) error.getLineNumber() - 1;
            int column = (int) error.getColumnNumber() - 1;

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
