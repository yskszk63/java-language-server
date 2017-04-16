package org.javacs;

import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import javax.tools.JavaFileObject;
import java.util.Optional;

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
        if (error instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper)
            error = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) error).d;

        JCDiagnostic diagnostic = (JCDiagnostic) error;
        DiagnosticSource source = diagnostic.getDiagnosticSource();

        return new Range(
            new Position(
                    source.getLineNumber((int) error.getStartPosition()) - 1,
                    source.getColumnNumber((int) error.getStartPosition(), true) - 1
            ),
            new Position(
                    source.getLineNumber((int) error.getEndPosition()) - 1,
                    source.getColumnNumber((int) error.getEndPosition(), true) - 1
            )
        );
    }

}