package org.javacs;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.Range;

public class DecorateFile {
    public int version;
    public List<Range> staticFields = new ArrayList<>(), instanceFields = new ArrayList<>();
}
