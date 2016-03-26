package com.fivetran.javac;

import com.fivetran.javac.CursorScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class GotoDefinitionVisitor extends CursorScanner {
    private final Logger LOG = Logger.getLogger("");

    public final Set<Symbol> definitions = new HashSet<>();

    public GotoDefinitionVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    @Override
    public void visitIdent(JCTree.JCIdent id) {
        super.visitIdent(id);

        if (!containsCursor(id))
            return;

        definitions.add(id.sym);
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        super.visitSelect(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(tree.getExpression()))
            super.visitSelect(tree);
        else
            definitions.add(tree.sym);
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(tree.getQualifierExpression()))
            super.visitReference(tree);
        else
            definitions.add(tree.sym);
    }
}
