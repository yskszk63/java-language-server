package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class GotoDefinitionVisitor extends CursorScanner {
    private static final Logger LOG = Logger.getLogger("");

    public final Set<SymbolLocation> definitions = new HashSet<>();

    public GotoDefinitionVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    @Override
    public void visitIdent(JCTree.JCIdent id) {
        super.visitIdent(id);

        if (!containsCursor(id))
            return;

        Symbol symbol = id.sym;

        addSymbol(symbol);
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        super.visitSelect(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(tree.getExpression()))
            super.visitSelect(tree);
        else {
            Symbol symbol = tree.sym;

            addSymbol(symbol);
        }
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(tree.getQualifierExpression()))
            super.visitReference(tree);
        else {
            Symbol symbol = tree.sym;

            addSymbol(symbol);
        }
    }

    private void addSymbol(Symbol symbol) {
        LOG.info("Goto " + symbol);

        context.get(ClassIndex.class).locate(symbol).map(definitions::add);
    }
}
