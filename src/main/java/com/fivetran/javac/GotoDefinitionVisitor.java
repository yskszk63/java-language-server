package com.fivetran.javac;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.FindSymbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
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

        FindSymbol.locate(context, id.sym).map(definitions::add);
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
            FindSymbol.locate(context, tree.sym).map(definitions::add);
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
            FindSymbol.locate(context, tree.sym).map(definitions::add);
    }
}
