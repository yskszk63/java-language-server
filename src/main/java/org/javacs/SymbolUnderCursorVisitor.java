package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.*;
import java.util.logging.Logger;

/**
 * Finds symbol under the cursor
 */
public class SymbolUnderCursorVisitor extends CursorScanner {
    private static final Logger LOG = Logger.getLogger("main");
    
    public Optional<Symbol> found = Optional.empty();

    public SymbolUnderCursorVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    @Override
    public void visitIdent(JCTree.JCIdent id) {
        super.visitIdent(id);

        if (!containsCursor(id))
            return;

        Symbol symbol = id.sym;

        found(symbol);
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        super.visitSelect(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (!containsCursor(tree.getExpression())) {
            Symbol symbol = tree.sym;

            found(symbol);
        }
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (!containsCursor(tree.getQualifierExpression())) {
            Symbol symbol = tree.sym;

            found(symbol);
        }
    }

    private void found(Symbol symbol) {
        found = Optional.of(symbol);
    }
}
