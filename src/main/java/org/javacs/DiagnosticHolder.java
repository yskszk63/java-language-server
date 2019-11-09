package org.javacs;

import com.sun.source.tree.LineMap;
import java.util.*;
import java.util.List;
import javax.tools.*;
import javax.tools.JavaFileObject;
import org.javacs.lsp.*;

class DiagnosticHolder {
    private final int severity;
    private final String code, message;
    private final List<Integer> tags; // DiagnosticTag
    int start, end;

    private DiagnosticHolder(int severity, String code, String message, List<Integer> tags, int start, int end) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.tags = tags;
        this.start = start;
        this.end = end;
    }

    static DiagnosticHolder wrap(javax.tools.Diagnostic<? extends JavaFileObject> java) {
        var start = (int) java.getStartPosition();
        var end = (int) java.getEndPosition();
        var severity = severity(java.getKind());
        var code = java.getCode();
        var message = java.getMessage(null);
        return new DiagnosticHolder(severity, code, message, List.of(), start, end);
    }

    static DiagnosticHolder warnUnused(int severity, String message, int start, int end) {
        return new DiagnosticHolder(severity, "unused", message, List.of(DiagnosticTag.Unnecessary), start, end);
    }

    private static int severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
                return DiagnosticSeverity.Information;
            case OTHER:
            default:
                return DiagnosticSeverity.Hint;
        }
    }

    boolean validRange() {
        return start >= 0 && end >= 0;
    }

    /**
     * lspDiagnostic() converts javaDiagnostic to LSP format, with its position shifted appropriately for the latest
     * version of the file.
     */
    org.javacs.lsp.Diagnostic lspDiagnostic(LineMap lines) {
        var result = new org.javacs.lsp.Diagnostic();
        result.severity = severity;
        result.code = code;
        result.message = message;
        result.tags = tags;
        result.range = new Span(start, end).asRange(lines);
        return result;
    }

    void shift(int offset) {
        start += offset;
        end += offset;
    }
}
