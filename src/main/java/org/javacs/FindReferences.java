package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;

class FindReferences extends TreePathScanner<Void, Map<Element, List<TreePath>>> {
    private final Trees trees;
    private final Elements elements;

    FindReferences(JavacTask task) {
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
    }

    boolean sameSymbol(Element from, Element to) {
        return to.equals(from);
    }

    boolean isSuperMethod(Element from, Element to) {
        if (!(to instanceof ExecutableElement)) return false;
        if (!(from instanceof ExecutableElement)) return false;
        var subMethod = (ExecutableElement) to;
        var subType = (TypeElement) subMethod.getEnclosingElement();
        var superMethod = (ExecutableElement) from;
        // TODO need to check if class is compatible as well
        if (elements.overrides(subMethod, superMethod, subType)) {
            // LOG.info(String.format("...`%s.%s` overrides `%s`", subType, subMethod, superMethod));
            return true;
        }
        return false;
    }

    void check(TreePath from, Map<Element, List<TreePath>> refs) {
        for (var to : refs.keySet()) {
            var fromEl = trees.getElement(from);
            var match = sameSymbol(fromEl, to) || isSuperMethod(fromEl, to);
            if (match) {
                refs.get(to).add(from);
            }
        }
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree t, Map<Element, List<TreePath>> refs) {
        check(getCurrentPath(), refs);
        return super.visitMemberReference(t, refs);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree t, Map<Element, List<TreePath>> refs) {
        check(getCurrentPath(), refs);
        return super.visitMemberSelect(t, refs);
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, Map<Element, List<TreePath>> refs) {
        check(getCurrentPath(), refs);
        return super.visitIdentifier(t, refs);
    }

    @Override
    public Void visitNewClass(NewClassTree t, Map<Element, List<TreePath>> refs) {
        check(getCurrentPath(), refs);
        return super.visitNewClass(t, refs);
    }
}
