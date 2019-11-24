package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

class WarnNotThrown extends TreePathScanner<Void, Map<String, TreePath>> {
    private final JavacTask task;
    private CompilationUnitTree root;
    private Map<String, TreePath> declaredExceptions = new HashMap<>();
    private Set<String> observedExceptions = new HashSet<>();

    WarnNotThrown(JavacTask task) {
        this.task = task;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree t, Map<String, TreePath> notThrown) {
        root = t;
        return super.visitCompilationUnit(t, notThrown);
    }

    @Override
    public Void visitMethod(MethodTree t, Map<String, TreePath> notThrown) {
        // Create a new method scope
        var pushDeclared = declaredExceptions;
        var pushObserved = observedExceptions;
        declaredExceptions = declared(t);
        observedExceptions = new HashSet<>();
        // Recursively scan for 'throw' and method calls
        super.visitMethod(t, notThrown);
        // Check for exceptions that were never thrown
        for (var declared : declaredExceptions.keySet()) {
            if (!observedExceptions.contains(declared)) {
                notThrown.put(declared, declaredExceptions.get(declared));
            }
        }
        declaredExceptions = pushDeclared;
        observedExceptions = pushObserved;
        return null;
    }

    private Map<String, TreePath> declared(MethodTree t) {
        var trees = Trees.instance(task);
        var names = new HashMap<String, TreePath>();
        for (var e : t.getThrows()) {
            var path = new TreePath(getCurrentPath(), e);
            var to = trees.getElement(path);
            if (!(to instanceof TypeElement)) continue;
            var type = (TypeElement) to;
            var name = type.getQualifiedName().toString();
            names.put(name, path);
        }
        return names;
    }

    @Override
    public Void visitThrow(ThrowTree t, Map<String, TreePath> notThrown) {
        var path = new TreePath(getCurrentPath(), t.getExpression());
        var type = Trees.instance(task).getTypeMirror(path);
        if (type instanceof DeclaredType) {
            var declared = (DeclaredType) type;
            var el = (TypeElement) declared.asElement();
            var name = el.getQualifiedName().toString();
            observedExceptions.add(name);
        }
        return super.visitThrow(t, notThrown);
    }
}
