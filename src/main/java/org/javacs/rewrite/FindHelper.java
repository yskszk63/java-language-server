package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.function.Predicate;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class FindHelper {
    final JavacTask task;

    FindHelper(CompileTask task) {
        this.task = task.task;
    }

    MethodTree findMethod(
            CompilationUnitTree root, String className, String methodName, String[] erasedParameterTypes) {
        Predicate<TreePath> test = path -> isSameMethod(path, className, methodName, erasedParameterTypes);
        return new FindMethodDeclaration().scan(root, test);
    }

    boolean isSameMethod(TreePath path, String className, String methodName, String[] erasedParameterTypes) {
        var trees = Trees.instance(task);
        var candidate = trees.getElement(path);
        if (!(candidate instanceof ExecutableElement)) return false;
        var method = (ExecutableElement) candidate;
        return isSameMethod(method, className, methodName, erasedParameterTypes);
    }

    boolean isSameMethod(ExecutableElement method, String className, String methodName, String[] erasedParameterTypes) {
        var types = task.getTypes();
        var parent = (TypeElement) method.getEnclosingElement();
        if (!parent.getQualifiedName().contentEquals(className)) return false;
        if (!method.getSimpleName().contentEquals(methodName)) return false;
        if (method.getParameters().size() != erasedParameterTypes.length) return false;
        for (var i = 0; i < erasedParameterTypes.length; i++) {
            var erasure = types.erasure(method.getParameters().get(i).asType());
            var same = erasure.toString().equals(erasedParameterTypes[i]);
            if (!same) return false;
        }
        return true;
    }
}
