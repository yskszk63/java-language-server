package org.javacs;

import com.sun.source.tree.LineMap;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;

class Span {
    final int start, until;

    Span(int start, int until) {
        this.start = start;
        this.until = until;
    }

    Range asRange(LineMap lines) {
        if (start == -1 || until == -1) {
            throw new RuntimeException(String.format("Range %d-%d is invalid", start, until));
        }
        var startLine = (int) lines.getLineNumber(start) - 1;
        var startColumn = (int) lines.getColumnNumber(start) - 1;
        var start = new Position(startLine, startColumn);
        var endLine = (int) lines.getLineNumber(until) - 1;
        var endColumn = (int) lines.getColumnNumber(until) - 1;
        var end = new Position(endLine, endColumn);
        return new Range(start, end);
    }

    static final Span INVALID = new Span(-1, -1);
    static final Span EMPTY = new Span(0, 0);
}
