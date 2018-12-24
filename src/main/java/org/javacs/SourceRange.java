package org.javacs;

import java.net.URI;
import java.util.Optional;

public class SourceRange {
    public final URI file;
    public final int startLine, startCol, endLine, endCol;
    public final Optional<String> className, memberName;

    public SourceRange(
            URI file,
            int startLine,
            int startCol,
            int endLine,
            int endCol,
            Optional<String> className,
            Optional<String> memberName) {
        this.file = file;
        this.startLine = startLine;
        this.startCol = startCol;
        this.endLine = endLine;
        this.endCol = endCol;
        this.className = className;
        this.memberName = memberName;
    }
}
