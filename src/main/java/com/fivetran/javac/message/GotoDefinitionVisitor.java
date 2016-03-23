package com.fivetran.javac.message;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class GotoDefinitionVisitor extends VisitCursor {
    private final Logger LOG = Logger.getLogger("");

    public final Set<Symbol> definitions = new HashSet<>();

    public GotoDefinitionVisitor(JavaFileObject file, long cursor) {
        super(file, cursor);
    }

    @Override
    protected void visitIdentifier(IdentifierTree id) {
        super.visitIdentifier(id);

        if (!containsCursor(id))
            return;

        Symbol symbol = ((JCTree.JCIdent) id).sym;

        definitions.add(symbol);
    }

    @Override
    protected void visitMemberReference(MemberReferenceTree node) {
        super.visitMemberReference(node);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(node) && !containsCursor(node.getQualifierExpression())) {
            Symbol symbol = ((JCTree.JCMemberReference) node).sym;

            definitions.add(symbol);
        }
    }

    @Override
    protected void visitMemberSelect(MemberSelectTree node) {
        super.visitMemberSelect(node);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(node) && !containsCursor(node.getExpression())) {
            Symbol symbol = ((JCTree.JCFieldAccess) node).sym;

            definitions.add(symbol);
        }
    }
}
