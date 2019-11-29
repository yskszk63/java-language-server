package org.javacs.navigation;

import com.sun.source.tree.*;
import com.sun.source.util.*;

class FindElementAt extends TreeScanner<Tree, Integer> {
    final JavacTask task;
    private CompilationUnitTree root;
    private ClassTree surroundingClass;

    FindElementAt(JavacTask task) {
        this.task = task;
    }

    @Override
    public Tree visitCompilationUnit(CompilationUnitTree t, Integer find) {
        root = t;
        return super.visitCompilationUnit(t, find);
    }

    @Override
    public Tree visitClass(ClassTree t, Integer find) {
        var push = surroundingClass;
        surroundingClass = t;
        if (contains(t, t.getSimpleName(), find)) {
            surroundingClass = push;
            return t;
        }
        var result = super.visitClass(t, find);
        surroundingClass = push;
        return result;
    }

    @Override
    public Tree visitMethod(MethodTree t, Integer find) {
        var name = t.getName();
        if (name.contentEquals("<init>")) {
            name = surroundingClass.getSimpleName();
        }
        if (contains(t, name, find)) {
            return t;
        }
        return super.visitMethod(t, find);
    }

    @Override
    public Tree visitIdentifier(IdentifierTree t, Integer find) {
        if (contains(t, t.getName(), find)) {
            return t;
        }
        return super.visitIdentifier(t, find);
    }

    @Override
    public Tree visitMemberSelect(MemberSelectTree t, Integer find) {
        if (contains(t, t.getIdentifier(), find)) {
            return t;
        }
        return super.visitMemberSelect(t, find);
    }

    @Override
    public Tree visitMemberReference(MemberReferenceTree t, Integer find) {
        if (contains(t, t.getName(), find)) {
            return t;
        }
        return super.visitMemberReference(t, find);
    }

    @Override
    public Tree visitVariable(VariableTree t, Integer find) {
        if (contains(t, t.getName(), find)) {
            return t;
        }
        return super.visitVariable(t, find);
    }

    @Override
    public Tree visitNewClass(NewClassTree t, Integer find) {
        var start = Trees.instance(task).getSourcePositions().getStartPosition(root, t);
        var end = start + "new".length();
        if (start <= find && find < end) {
            return t;
        }
        return super.visitNewClass(t, find);
    }

    @Override
    public Tree reduce(Tree r1, Tree r2) {
        if (r1 != null) return r1;
        return r2;
    }

    private boolean contains(Tree t, CharSequence name, int find) {
        var pos = Trees.instance(task).getSourcePositions();
        var start = (int) pos.getStartPosition(root, t);
        var end = (int) pos.getEndPosition(root, t);
        if (start == -1 || end == -1) return false;
        start = NavigationHelper.findNameIn(root, name, start, end);
        end = start + name.length();
        if (start == -1 || end == -1) return false;
        return start <= find && find < end;
    }
}
