package org.javacs;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public class FindHelper {

    public static String[] erasedParameterTypes(CompileTask task, ExecutableElement method) {
        var types = task.task.getTypes();
        var erasedParameterTypes = new String[method.getParameters().size()];
        for (var i = 0; i < erasedParameterTypes.length; i++) {
            var p = method.getParameters().get(i).asType();
            erasedParameterTypes[i] = types.erasure(p).toString();
        }
        return erasedParameterTypes;
    }

    public static MethodTree findMethod(
            ParseTask task, String className, String methodName, String[] erasedParameterTypes) {
        var classTree = findType(task, className);
        for (var member : classTree.getMembers()) {
            if (member.getKind() != Tree.Kind.METHOD) continue;
            var method = (MethodTree) member;
            if (!method.getName().contentEquals(methodName)) continue;
            if (!isSameMethodType(method, erasedParameterTypes)) continue;
            return method;
        }
        throw new RuntimeException("no method");
    }

    public static VariableTree findField(ParseTask task, String className, String memberName) {
        var classTree = findType(task, className);
        for (var member : classTree.getMembers()) {
            if (member.getKind() != Tree.Kind.VARIABLE) continue;
            var variable = (VariableTree) member;
            if (!variable.getName().contentEquals(memberName)) continue;
            return variable;
        }
        throw new RuntimeException("no variable");
    }

    public static ClassTree findType(ParseTask task, String className) {
        return new FindTypeDeclarationNamed().scan(task.root, className);
    }

    public static ExecutableElement findMethod(
            CompileTask task, String className, String methodName, String[] erasedParameterTypes) {
        var type = task.task.getElements().getTypeElement(className);
        for (var member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            var method = (ExecutableElement) member;
            if (isSameMethod(task, method, className, methodName, erasedParameterTypes)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isSameMethod(
            CompileTask task,
            ExecutableElement method,
            String className,
            String methodName,
            String[] erasedParameterTypes) {
        var types = task.task.getTypes();
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

    private static boolean isSameMethodType(MethodTree candidate, String[] erasedParameterTypes) {
        if (candidate.getParameters().size() != erasedParameterTypes.length) {
            return false;
        }
        for (var i = 0; i < candidate.getParameters().size(); i++) {
            if (!typeMatches(candidate.getParameters().get(i).getType(), erasedParameterTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean typeMatches(Tree candidate, String erasedType) {
        if (candidate instanceof ParameterizedTypeTree) {
            var parameterized = (ParameterizedTypeTree) candidate;
            return typeMatches(parameterized.getType(), erasedType);
        }
        if (candidate instanceof PrimitiveTypeTree) {
            return candidate.toString().equals(erasedType);
        }
        if (candidate instanceof IdentifierTree) {
            var simpleName = candidate.toString();
            return erasedType.endsWith(simpleName);
        }
        if (candidate instanceof MemberSelectTree) {
            return candidate.toString().equals(erasedType);
        }
        if (candidate instanceof ArrayTypeTree) {
            var array = (ArrayTypeTree) candidate;
            if (!erasedType.endsWith("[]")) return false;
            var erasedElement = erasedType.substring(0, erasedType.length() - "[]".length());
            return typeMatches(array.getType(), erasedElement);
        }
        return true;
    }
}
