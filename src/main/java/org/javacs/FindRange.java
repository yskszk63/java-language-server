package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.Objects;

// Search for the smallest element that encompasses a range
class FindRange extends TreePathScanner<Void, Void> {
    private final SourcePositions pos;
    private final long start, end;
    private final CompilationUnitTree root;
    TreePath found = null;

    FindRange(long start, long end, JavacTask task, CompilationUnitTree root) {
        var trees = Trees.instance(task);
        this.pos = trees.getSourcePositions();
        this.start = start;
        this.end = end;
        this.root = root;
    }

    boolean containsRange(Tree tree) {
        Objects.requireNonNull(root, "root was never set");
        long start = pos.getStartPosition(root, tree), end = pos.getEndPosition(root, tree);
        return start <= this.start && this.end <= end;
    }

    @Override
    public Void scan(Tree tree, Void nothing) {
        // This is pre-order traversal, so the deepest element will be the last one remaining in `found`
        if (containsRange(tree)) {
            found = new TreePath(getCurrentPath(), tree);
        }
        super.scan(tree, nothing);
        return null;
    }

    @Override
    public Void visitErroneous(ErroneousTree node, Void nothing) {
        if (node.getErrorTrees() == null) return null;
        for (var t : node.getErrorTrees()) {
            scan(t, nothing);
        }
        return null;
    }
}
