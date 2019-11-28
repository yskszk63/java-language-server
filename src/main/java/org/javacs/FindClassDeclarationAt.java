package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

public class FindClassDeclarationAt extends TreeScanner<ClassTree, Long> {
    private final SourcePositions pos;
    private CompilationUnitTree root;

    public FindClassDeclarationAt(JavacTask task) {
        pos = Trees.instance(task).getSourcePositions();
    }

    @Override
    public ClassTree visitCompilationUnit(CompilationUnitTree t, Long find) {
        root = t;
        return super.visitCompilationUnit(t, find);
    }

    @Override
    public ClassTree visitClass(ClassTree t, Long find) {
        var smaller = super.visitClass(t, find);
        if (smaller != null) {
            return smaller;
        }
        if (pos.getStartPosition(root, t) <= find && find < pos.getEndPosition(root, t)) {
            return t;
        }
        return null;
    }

    @Override
    public ClassTree reduce(ClassTree r1, ClassTree r2) {
        if (r1 != null) return r1;
        return r2;
    }
}
