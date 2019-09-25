package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

class FindMissingOverride extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final Elements elements;
    private final Types types;
    final List<TreePath> results = new ArrayList<>();

    FindMissingOverride(JavacTask task) {
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.types = task.getTypes();
    }

    @Override
    public Void visitMethod(MethodTree t, Void __) {
        var method = (ExecutableElement) trees.getElement(getCurrentPath());
        var supers = overrides(method);
        if (!supers.isEmpty() && !hasOverrideAnnotation(method)) {
            var overridesMethod = supers.get(0);
            var overridesClass = overridesMethod.getEnclosingElement();
            LOG.info(
                    String.format(
                            "...`%s` has no @Override annotation but overrides `%s.%s`",
                            method, overridesClass, overridesMethod));
            results.add(getCurrentPath());
        }
        return super.visitMethod(t, null);
    }

    private boolean hasOverrideAnnotation(ExecutableElement method) {
        for (var ann : method.getAnnotationMirrors()) {
            var type = ann.getAnnotationType();
            var el = type.asElement();
            var name = el.toString();
            if (name.equals("java.lang.Override")) {
                return true;
            }
        }
        return false;
    }

    private List<Element> overrides(ExecutableElement method) {
        var results = new ArrayList<Element>();
        var enclosingClass = (TypeElement) method.getEnclosingElement();
        var enclosingType = enclosingClass.asType();
        for (var superClass : types.directSupertypes(enclosingType)) {
            var e = (TypeElement) types.asElement(superClass);
            for (var other : e.getEnclosedElements()) {
                if (!(other instanceof ExecutableElement)) continue;
                if (elements.overrides(method, (ExecutableElement) other, enclosingClass)) {
                    results.add(other);
                }
            }
        }
        return results;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
