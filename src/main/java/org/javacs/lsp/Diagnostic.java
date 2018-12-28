package org.javacs.lsp;

public class Diagnostic {
    public Range range;
    public int severity;
    public String code, source, message;
}
