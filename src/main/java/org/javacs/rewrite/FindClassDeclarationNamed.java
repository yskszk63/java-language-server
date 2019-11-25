package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO this might be replaced with Elements
class FindClassDeclarationNamed extends TreeScanner<ClassTree, String> {
    private CompilationUnitTree root;
    private List<CharSequence> qualifiedName = new ArrayList<>();

    @Override
    public ClassTree visitCompilationUnit(CompilationUnitTree t, String find) {
        root = t;
        var name = Objects.toString(t.getPackage().getPackageName(), "");
        qualifiedName.add(name);
        return super.visitCompilationUnit(t, find);
    }

    @Override
    public ClassTree visitClass(ClassTree t, String find) {
        qualifiedName.add(t.getSimpleName());
        var smaller = super.visitClass(t, find);
        if (smaller != null) {
            return smaller;
        }
        if (String.join(".", qualifiedName).equals(find)) {
            return t;
        }
        qualifiedName.remove(qualifiedName.size() - 1);
        return null;
    }

    @Override
    public ClassTree reduce(ClassTree r1, ClassTree r2) {
        if (r1 != null) return r1;
        return r2;
    }
}
