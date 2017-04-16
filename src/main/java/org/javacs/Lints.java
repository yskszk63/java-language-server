package org.javacs;

import java.io.*;
import java.net.URI;
import java.util.*;
import javax.tools.JavaFileObject;
import org.eclipse.lsp4j.*;

class Lints {

    static Optional<Diagnostic> convert(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        if (error.getStartPosition() != javax.tools.Diagnostic.NOPOS) {
            Range range = position(error);
            Diagnostic diagnostic = new Diagnostic();
            DiagnosticSeverity severity = severity(error.getKind());

            diagnostic.setSeverity(severity);
            diagnostic.setRange(range);
            diagnostic.setCode(error.getCode());
            diagnostic.setMessage(error.getMessage(null));

            return Optional.of(diagnostic);
        }
        else return Optional.empty();
    }

    private static DiagnosticSeverity severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
            case OTHER:
            default:
                return DiagnosticSeverity.Information;
        }
    }

    private static Range position(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        // Compute start position
        Position start = new Position();

        start.setLine((int) (error.getLineNumber() - 1));
        start.setCharacter((int) (error.getColumnNumber() - 1));

        // Compute end position
        Position end = endPosition(error);

        // Combine into Range
        Range range = new Range();

        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    private static Position endPosition(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        try (Reader reader = error.getSource().openReader(true)) {
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

            Position end = new Position();

            end.setLine(line);
            end.setCharacter(column);

            return end;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}