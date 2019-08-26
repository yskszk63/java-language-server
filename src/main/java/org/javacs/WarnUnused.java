package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.*;
import javax.lang.model.element.*;

class WarnUnused extends TreePathScanner<Void, Void> {
    private final Trees trees;
    // TODO ignore writes when calculating used
    private final Set<Element> reachable = new HashSet<>(), unused = new HashSet<>();

    WarnUnused(JavacTask task) {
        this.trees = Trees.instance(task);
    }

    Set<Element> notUsed() {
        unused.removeAll(reachable);
        return unused;
    }

    private void foundPrivateDeclaration() {
        unused.add(trees.getElement(getCurrentPath()));
    }

    private void foundReference() {
        var fromPath = getCurrentPath();
        var toEl = trees.getElement(fromPath);
        var toPath = trees.getPath(toEl);
        if (toPath == null) return;
        var fromUri = fromPath.getCompilationUnit().getSourceFile().toUri();
        var toUri = toPath.getCompilationUnit().getSourceFile().toUri();
        if (!fromUri.equals(toUri)) return;
        if (isLocalVariable(toPath)) {
            reachable.add(toEl);
        }
        if (!isReachable(toPath)) {
            reachable.add(toEl);
            scan(toPath, null);
        }
    }

    private boolean isReachable(TreePath path) {
        // Check if t is reachable because it's public
        var t = path.getLeaf();
        if (t instanceof VariableTree) {
            var v = (VariableTree) t;
            var isPrivate = v.getModifiers().getFlags().contains(Modifier.PRIVATE);
            if (!isPrivate || isLocalVariable(path)) {
                return true;
            }
        }
        if (t instanceof MethodTree) {
            var m = (MethodTree) t;
            var isPrivate = m.getModifiers().getFlags().contains(Modifier.PRIVATE);
            var isEmptyConstructor = m.getParameters().isEmpty() && m.getReturnType() == null;
            if (!isPrivate || isEmptyConstructor) {
                return true;
            }
        }
        if (t instanceof ClassTree) {
            var c = (ClassTree) t;
            var isPrivate = c.getModifiers().getFlags().contains(Modifier.PRIVATE);
            if (!isPrivate) {
                return true;
            }
        }
        // Check if t has been referenced by a reachable element
        var el = trees.getElement(path);
        if (reachable.contains(el)) {
            return true;
        }
        return false;
    }

    private boolean isLocalVariable(TreePath path) {
        if (path.getLeaf() instanceof VariableTree) {
            var parent = path.getParentPath().getLeaf();
            return !(parent instanceof ClassTree)
                    && !(parent instanceof MethodTree) // TODO hint for unused parameters
                    && !(parent instanceof LambdaExpressionTree);
        }
        return false;
    }

    @Override
    public Void visitVariable(VariableTree t, Void __) {
        if (isLocalVariable(getCurrentPath())) {
            foundPrivateDeclaration();
            super.visitVariable(t, null);
        } else if (isReachable(getCurrentPath())) {
            super.visitVariable(t, null);
        } else {
            foundPrivateDeclaration();
        }
        return null;
    }

    @Override
    public Void visitMethod(MethodTree t, Void __) {
        if (isReachable(getCurrentPath())) {
            super.visitMethod(t, null);
        } else {
            foundPrivateDeclaration();
        }
        return null;
    }

    @Override
    public Void visitClass(ClassTree t, Void __) {
        if (isReachable(getCurrentPath())) {
            super.visitClass(t, null);
        } else {
            foundPrivateDeclaration();
        }
        return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, Void __) {
        foundReference();
        return super.visitIdentifier(t, null);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree t, Void __) {
        foundReference();
        return super.visitMemberSelect(t, null);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree t, Void __) {
        foundReference();
        return super.visitMemberReference(t, null);
    }

    @Override
    public Void visitNewClass(NewClassTree t, Void __) {
        foundReference();
        return super.visitNewClass(t, null);
    }
}
