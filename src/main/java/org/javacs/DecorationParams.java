package org.javacs;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.Range;

public class DecorationParams {
    public URI uri;
    public List<Range> fields = new ArrayList<>();
}
