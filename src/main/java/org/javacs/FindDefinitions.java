package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;

class FindDefinitions extends TreePathScanner<Void, Void> {
    private final Element el;
    private final Trees trees;
    private final Elements elements;
    final List<TreePath> results = new ArrayList<>();

    FindDefinitions(Element el, JavacTask task) {
        this.el = el;
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
    }

    boolean sameSymbol(Element found) {
        return el.equals(found);
    }

    boolean isSubMethod(Element found) {
        if (!(el instanceof ExecutableElement)) return false;
        if (!(found instanceof ExecutableElement)) return false;
        var superMethod = (ExecutableElement) el;
        var subMethod = (ExecutableElement) found;
        var subType = (TypeElement) subMethod.getEnclosingElement();
        // TODO need to check if class is compatible as well
        if (elements.overrides(subMethod, superMethod, subType)) {
            // LOG.info(String.format("...`%s.%s` overrides `%s`", subType, subMethod, superMethod));
            return true;
        }
        return false;
    }

    void check(TreePath from) {
        var found = trees.getElement(from);
        var match = sameSymbol(found) || isSubMethod(found);
        if (match) results.add(from);
    }

    @Override
    public Void visitClass(ClassTree t, Void __) {
        check(getCurrentPath());
        return super.visitClass(t, null);
    }

    @Override
    public Void visitMethod(MethodTree t, Void __) {
        check(getCurrentPath());
        return super.visitMethod(t, null);
    }

    @Override
    public Void visitVariable(VariableTree t, Void __) {
        check(getCurrentPath());
        return super.visitVariable(t, null);
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree t, Void __) {
        check(getCurrentPath());
        return super.visitTypeParameter(t, null);
    }
}
