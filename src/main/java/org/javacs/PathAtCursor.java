package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;

import java.util.Optional;
import java.util.function.Function;

/**
 * Find the Tree that the cursor is pointing apply
 */
public class PathAtCursor extends CursorScanner<TreePath> {

    private PathAtCursor(JavacTask task, int line, int column) {
        super(task, line, column);
    }

    public static Function<CompilationUnitTree, Optional<TreePath>> create(JavacTask task, int line, int column) {
        return new PathAtCursor(task, line, column);
    }

    @Override
    public TreePath visitMemberReference(MemberReferenceTree node, Void aVoid) {
        if (containsCursor(node) && !containsCursor(node.getQualifierExpression()))
            return getCurrentPath();
        else
            return super.visitMemberReference(node, aVoid);
    }

    @Override
    public TreePath visitMemberSelect(MemberSelectTree node, Void aVoid) {
        if (containsCursor(node) && !containsCursor(node.getExpression()))
            return getCurrentPath();
        else
            return super.visitMemberSelect(node, aVoid);
    }

    @Override
    public TreePath visitNewClass(NewClassTree node, Void aVoid) {
        if (containsCursor(node) && node.getClassBody() == null)
            return getCurrentPath();
        else
            return super.visitNewClass(node, aVoid);
    }

    @Override
    public TreePath visitIdentifier(IdentifierTree node, Void aVoid) {
        if (containsCursor(node))
            return getCurrentPath();
        else
            return super.visitIdentifier(node, aVoid);
    }

    @Override
    public TreePath visitErroneous(ErroneousTree node, Void aVoid) {
        return super.scan(node.getErrorTrees(), aVoid);
    }
}