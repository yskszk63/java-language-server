package org.javacs.rewrite;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.*;
import java.util.function.Predicate;

// TODO this might be replaced with Elements
class FindMethodDeclaration extends TreePathScanner<MethodTree, Predicate<TreePath>> {
    @Override
    public MethodTree visitMethod(MethodTree t, Predicate<TreePath> test) {
        if (test.test(getCurrentPath())) {
            return t;
        }
        return super.visitMethod(t, test);
    }

    @Override
    public MethodTree reduce(MethodTree r1, MethodTree r2) {
        if (r1 != null) return r1;
        return r2;
    }
}
