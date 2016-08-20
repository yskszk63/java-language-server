package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import javax.tools.JavaFileObject;
import java.util.logging.Logger;

/**
 * Removes all statements after the cursor
 */
// TODO if we ever implement incremental parsing, this will need to make a partial copy of the AST rather than modify it
public class AutocompletePruner extends CursorScanner {
    public AutocompletePruner(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        tree.stats = pruneStatements(tree.stats);

        super.visitBlock(tree);
    }

    @Override
    public void visitCase(JCTree.JCCase tree) {
        tree.stats = pruneStatements(tree.stats);

        super.visitCase(tree);
    }

    @Override
    public void visitSwitch(JCTree.JCSwitch tree) {
        tree.cases = pruneStatements(tree.cases);

        super.visitSwitch(tree);
    }

    private <T extends JCTree> List<T> pruneStatements(List<T> stats) {
        int countStatements = 0;

        // Scan up to statement containing cursor
        while (countStatements < stats.size()) {
            T s = stats.get(countStatements);

            if (containsCursor(s))
                break;
            else
                s.accept(this);

            countStatements++;
        }

        // Advance over statement containing cursor
        countStatements++;

        // Remove all statements after statement containing cursor
        return stats.take(countStatements);
    }
}
