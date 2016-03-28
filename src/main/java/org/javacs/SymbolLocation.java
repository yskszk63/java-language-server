package org.javacs;

import javax.tools.JavaFileObject;

public class SymbolLocation {
    public final JavaFileObject file;
    public final long startPosition, endPosition;

    public SymbolLocation(JavaFileObject file, long startPosition, long endPosition) {
        this.file = file;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }
}
