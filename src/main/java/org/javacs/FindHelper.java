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
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

public class FindHelper {

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

    public static MethodTree findMethod(ParseTask task, ExecutableElement element) {
        var classElement = (TypeElement) element.getEnclosingElement();
        var classTree = findType(task, classElement);
        if (classTree == null) return null;
        for (var member : classTree.getMembers()) {
            if (member.getKind() != Tree.Kind.METHOD) continue;
            var method = (MethodTree) member;
            // TODO does this work for constructors?
            if (!method.getName().contentEquals(element.getSimpleName())) continue;
            if (!FindHelper.isSameMethodType(method, (ExecutableType) element.asType())) continue;
            return method;
        }
        return null;
    }

    public static VariableTree findField(ParseTask task, Element element) {
        var classElement = (TypeElement) element.getEnclosingElement();
        var classTree = findType(task, classElement);
        if (classTree == null) return null;
        for (var member : classTree.getMembers()) {
            if (member.getKind() != Tree.Kind.VARIABLE) continue;
            var field = (VariableTree) member;
            if (!field.getName().contentEquals(element.getSimpleName())) continue;
            return field;
        }
        return null;
    }

    public static ClassTree findType(ParseTask task, TypeElement element) {
        var className = element.getQualifiedName().toString();
        return new FindTypeDeclarationNamed().scan(task.root, className);
    }

    private static boolean isSameMethodType(MethodTree candidate, ExecutableType find) {
        if (candidate.getParameters().size() != find.getParameterTypes().size()) {
            return false;
        }
        if (!typeMatches(candidate.getReturnType(), find.getReturnType())) {
            return false;
        }
        for (var i = 0; i < candidate.getParameters().size(); i++) {
            if (!typeMatches(candidate.getParameters().get(i), find.getParameterTypes().get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean typeMatches(Tree candidate, TypeMirror find) {
        if (find instanceof PrimitiveType) {
            return candidate.toString().equals(find.toString());
        } else if (find instanceof DeclaredType) {
            var declared = (DeclaredType) find;
            var name = declared.asElement().getSimpleName();
            var pattern = Pattern.compile("^(\\w\\.)*" + name);
            return pattern.matcher(candidate.toString()).find();
        } else if (find instanceof ArrayType) {
            if (!(candidate instanceof ArrayTypeTree)) {
                return false;
            }
            var findArray = (ArrayType) find;
            var candidateArray = (ArrayTypeTree) candidate;
            return typeMatches(candidateArray.getType(), findArray.getComponentType());
        } else {
            return true;
        }
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
