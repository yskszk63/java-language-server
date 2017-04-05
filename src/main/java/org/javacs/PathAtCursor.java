package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;

import java.util.Collection;
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
    public TreePath visitMethod(MethodTree node, Void aVoid) {
        if (methodNameContainsCursor(node))
            return getCurrentPath();
        else
            return super.visitMethod(node, aVoid);
    }

    private boolean methodNameContainsCursor(MethodTree node) {
        return containsCursor(node) && 
                !listContainsCursor(node.getParameters()) &&
                !listContainsCursor(node.getThrows()) &&
                !listContainsCursor(node.getTypeParameters()) &&
                !containsCursor(node.getBody()) &&
                !containsCursor(node.getDefaultValue()) &&
                !containsCursor(node.getModifiers()) &&
                !containsCursor(node.getReceiverParameter()) &&
                !containsCursor(node.getReturnType());
    }

    @Override
    public TreePath visitClass(ClassTree node, Void aVoid) {
        if (classNameContainsCursor(node))
            return getCurrentPath();
        else
            return super.visitClass(node, aVoid);
    }

    private boolean classNameContainsCursor(ClassTree node) {
        return containsCursor(node) &&
                !listContainsCursor(node.getTypeParameters()) &&
                !listContainsCursor(node.getImplementsClause()) &&
                !listContainsCursor(node.getMembers()) &&
                !containsCursor(node.getModifiers()) &&
                !containsCursor(node.getExtendsClause());
    }

    @Override
    public TreePath visitVariable(VariableTree node, Void aVoid) {
        if (variableNameContainsCursor(node))
            return getCurrentPath();
        else
            return super.visitVariable(node, aVoid);
    }

    private boolean variableNameContainsCursor(VariableTree node) {
        return containsCursor(node) &&
                !containsCursor(node.getModifiers()) &&
                !containsCursor(node.getInitializer()) &&
                !containsCursor(node.getType());
    }

    private boolean listContainsCursor(Collection<? extends Tree> list) {
        for (Tree each : list) {
            if (containsCursor(each))
                return true;
        }

        return false;
    }

    @Override
    public TreePath visitErroneous(ErroneousTree node, Void aVoid) {
        return super.scan(node.getErrorTrees(), aVoid);
    }
}