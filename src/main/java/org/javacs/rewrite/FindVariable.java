package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.function.Predicate;

class FindVariable extends TreeScanner<VariableTree, Predicate<VariableTree>> {
    @Override
    public VariableTree visitVariable(VariableTree t, Predicate<VariableTree> test) {
        if (test.test(t)) {
            return t;
        }
        return super.visitVariable(t, test);
    }

    @Override
    public VariableTree reduce(VariableTree r1, VariableTree r2) {
        if (r1 != null) return r1;
        return r2;
    }
}
