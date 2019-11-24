package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.function.Predicate;

class FindClassDeclaration extends TreeScanner<ClassTree, Predicate<ClassTree>> {
    @Override
    public ClassTree visitClass(ClassTree t, Predicate<ClassTree> test) {
        if (test.test(t)) {
            return t;
        }
        return super.visitClass(t, test);
    }

    @Override
    public ClassTree reduce(ClassTree r1, ClassTree r2) {
        if (r1 != null) return r1;
        return r2;
    }
}
