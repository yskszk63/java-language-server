package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.*;

class FindUsedImports extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final CompilationUnitTree root;
    final List<String> references = new ArrayList<>();

    FindUsedImports(JavacTask task, CompilationUnitTree root) {
        this.trees = Trees.instance(task);
        this.root = root;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void __) {
        Objects.requireNonNull(root, "root was never set");
        var e = trees.getElement(getCurrentPath());
        if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            var qualifiedName = t.getQualifiedName().toString();
            var lastDot = qualifiedName.lastIndexOf('.');
            var packageName = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
            var thisPackage = Objects.toString(root.getPackageName(), "");
            // java.lang.* and current package are imported by default
            if (!packageName.equals("java.lang") && !packageName.equals(thisPackage) && !packageName.equals("")) {
                references.add(qualifiedName);
            }
        }
        return null;
    }
}
