package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import java.util.List;
import java.util.Objects;

class FindImports extends TreeScanner<Void, List<String>> {

    @Override
    public Void visitCompilationUnit(CompilationUnitTree root, List<String> found) {
        var name = Objects.toString(root.getPackage().getPackageName(), "");
        found.add(name + ".*");
        return super.visitCompilationUnit(root, found);
    }

    @Override
    public Void visitImport(ImportTree t, List<String> found) {
        var name = Objects.toString(t.getQualifiedIdentifier(), "");
        found.add(name);
        return null;
    }
}
