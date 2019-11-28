package org.javacs.rewrite;

import com.sun.source.util.JavacTask;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class FindHelper {
    final JavacTask task;

    FindHelper(CompileTask task) {
        this.task = task.task;
    }

    ExecutableElement findMethod(String className, String methodName, String[] erasedParameterTypes) {
        var type = task.getElements().getTypeElement(className);
        for (var member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            var method = (ExecutableElement) member;
            if (isSameMethod(method, className, methodName, erasedParameterTypes)) {
                return method;
            }
        }
        return null;
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
