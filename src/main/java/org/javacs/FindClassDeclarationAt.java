package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class FindClassDeclarationAt extends TreeScanner<String, Long> {
    private final SourcePositions pos;
    private CompilationUnitTree root;
    private List<CharSequence> qualifiedName = new ArrayList<>();

    FindClassDeclarationAt(JavacTask task) {
        pos = Trees.instance(task).getSourcePositions();
    }

    @Override
    public String visitCompilationUnit(CompilationUnitTree t, Long find) {
        root = t;
        var name = Objects.toString(t.getPackage().getPackageName(), "");
        qualifiedName.add(name);
        return super.visitCompilationUnit(t, find);
    }

    @Override
    public String visitClass(ClassTree t, Long find) {
        qualifiedName.add(t.getSimpleName());
        var smaller = super.visitClass(t, find);
        if (smaller != null) {
            return smaller;
        }
        if (pos.getStartPosition(root, t) <= find && find < pos.getEndPosition(root, t)) {
            return String.join(".", qualifiedName);
        }
        qualifiedName.remove(qualifiedName.size() - 1);
        return null;
    }

    @Override
    public String reduce(String r1, String r2) {
        if (r1 != null) return r1;
        return r2;
    }
}
