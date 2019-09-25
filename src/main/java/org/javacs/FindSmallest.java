package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

// Search for the smallest element that encompasses line:column
class FindSmallest extends TreePathScanner<Void, Void> {
    private final SourcePositions pos;
    private final long cursor;
    private final CompilationUnitTree root;
    TreePath found = null;

    FindSmallest(long cursor, JavacTask task, CompilationUnitTree root) {
        var trees = Trees.instance(task);
        this.pos = trees.getSourcePositions();
        this.cursor = cursor;
        this.root = root;
    }

    boolean containsCursor(Tree tree) {
        Objects.requireNonNull(root, "root was never set");
        long start = pos.getStartPosition(root, tree), end = pos.getEndPosition(root, tree);
        // If cursor isn't in tree, return early
        if (cursor < start || end < cursor) return false;
        // int x = 1, y = 2, ... requires special handling
        if (tree instanceof VariableTree) {
            var v = (VariableTree) tree;
            // Get contents of source
            String source;
            try {
                source = root.getSourceFile().getCharContent(true).toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Find name in contents
            // TODO this picks up the `i` in `int` in `int i = 1;`
            var name = v.getName().toString();
            start = source.indexOf(name, (int) start);
            if (start == -1) {
                LOG.warning(String.format("Can't find name `%s` in variable declaration `%s`", name, v));
                return false;
            }
            end = start + name.length();
            // Check narrowed range
            return start <= cursor && cursor <= end;
        }
        return true;
    }

    @Override
    public Void scan(Tree tree, Void nothing) {
        // This is pre-order traversal, so the deepest element will be the last one remaining in `found`
        if (containsCursor(tree)) {
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

    private static final Logger LOG = Logger.getLogger("main");
}
