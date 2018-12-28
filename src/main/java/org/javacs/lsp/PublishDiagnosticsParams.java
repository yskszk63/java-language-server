package org.javacs.lsp;

import java.net.URI;
import java.util.List;

public class PublishDiagnosticsParams {
    public URI uri;
    public List<Diagnostic> diagnostics;
}
