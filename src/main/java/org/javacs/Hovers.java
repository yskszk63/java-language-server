package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

public class Hovers {
    public static Optional<Hover> hoverText(FocusedResult compiled) {
        Trees trees = Trees.instance(compiled.task);
        Hovers hovers = new Hovers(compiled.task);

        return compiled.cursor
                .flatMap(hovers::apply)
                .map(text -> new Hover(Collections.singletonList(Either.forLeft(text)), null));
    }

    private final Trees trees;

    private Hovers(JavacTask task) {
        trees = Trees.instance(task);
    }

    private Optional<String> apply(TreePath path) {
        return title(path).map(title -> {
            String docstring = trees.getDocComment(path);

            if (docstring != null)
                return String.format("```java\n%s\n```\n%s", title, docstring);
            else 
                return String.format("```java\n%s\n```", title);
        });
    }

    private Optional<String> title(TreePath path) {
        Element element = trees.getElement(path);

        if (element == null)
            return Optional.empty();

        Optional<TypeMirror> type = type(element);

        switch (element.getKind()) {
            case PACKAGE: {
                PackageElement p = (PackageElement) element;

                return Optional.of(p.getQualifiedName().toString());
            }
            case ENUM:
            case CLASS:
            case ANNOTATION_TYPE:
            case INTERFACE: {
                TypeElement t = (TypeElement) element;

                return Optional.of(t.getQualifiedName().toString());
            }
            case METHOD:
                return Optional.of(methodSignature((Symbol.MethodSymbol) element, true, true));
            case CONSTRUCTOR:
            case STATIC_INIT:
            case INSTANCE_INIT:
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case ENUM_CONSTANT:
            case FIELD: 
                return type.map(ShortTypePrinter::print);
            case TYPE_PARAMETER:
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                return Optional.empty();
        }
    }

    private Optional<TypeMirror> type(Element element) {
        TreePath path = trees.getPath(element);

        if (path == null)
            return Optional.empty();

        return Optional.ofNullable(trees.getTypeMirror(path));
    }

    public static String methodSignature(ExecutableElement e, boolean showReturn, boolean showMethodName) {
        String name = e.getKind() == ElementKind.CONSTRUCTOR ? constructorName(e) : e.getSimpleName().toString();
        boolean varargs = e.isVarArgs();
        StringJoiner params = new StringJoiner(", ");

        List<? extends VariableElement> parameters = e.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement p = parameters.get(i);
            String pName = shortName(p, varargs && i == parameters.size() - 1);

            params.add(pName);
        }

        String signature = "";
        
        if (showReturn)
            signature += ShortTypePrinter.print(e.getReturnType()) + " ";

        if (showMethodName)
            signature += e.getSimpleName();

        signature += "(" + params + ")";

        if (!e.getThrownTypes().isEmpty()) {
            StringJoiner thrown = new StringJoiner(", ");

            for (TypeMirror t : e.getThrownTypes())
                thrown.add(ShortTypePrinter.print(t));

            signature += " throws " + thrown;
        }

        return signature;
    }

    private static String shortName(VariableElement p, boolean varargs) {
        TypeMirror type = p.asType();

        if (varargs) {
            Type.ArrayType array = (Type.ArrayType) type;

            type = array.getComponentType();
        }

        String acc = shortTypeName(type);
        String name = p.getSimpleName().toString();

        if (varargs)
            acc += "...";

        if (!name.matches("arg\\d+"))
            acc += " " + name;

        return acc;
    }

    private static String shortTypeName(TypeMirror type) {
        return ShortTypePrinter.print(type);
    }

    private static String constructorName(ExecutableElement e) {
        return e.getEnclosingElement().getSimpleName().toString();
    }
}
