package org.javacs;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.Range;

class SemanticColors {
    URI uri;
    List<Range> statics = new ArrayList<>(), fields = new ArrayList<>();
}
