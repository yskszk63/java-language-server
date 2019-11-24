package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.*;

class FindUsedImports extends TreePathScanner<Void, Set<String>> {
    private final Trees trees;

    FindUsedImports(JavacTask task) {
        this.trees = Trees.instance(task);
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, Set<String> references) {
        var e = trees.getElement(getCurrentPath());
        if (e instanceof TypeElement) {
            var type = (TypeElement) e;
            var qualifiedName = type.getQualifiedName().toString();
            var lastDot = qualifiedName.lastIndexOf('.');
            var packageName = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
            var thisPackage = Objects.toString(getCurrentPath().getCompilationUnit().getPackageName(), "");
            // java.lang.* and current package are imported by default
            if (!packageName.equals("java.lang") && !packageName.equals(thisPackage) && !packageName.equals("")) {
                references.add(qualifiedName);
            }
        }
        return null;
    }
}
