package org.javacs;

import com.sun.source.util.TreePath;
import java.util.List;

public class FoldingRanges {
    public final List<TreePath> imports, blocks, comments;

    public FoldingRanges(List<TreePath> imports, List<TreePath> blocks, List<TreePath> comments) {
        this.imports = imports;
        this.blocks = blocks;
        this.comments = comments;
    }
}
