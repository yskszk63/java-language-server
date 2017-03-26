package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

public class Hovers implements Function<TreePath, Optional<String>> {
    public static Optional<Hover> hoverText(FocusedResult compiled) {
        Trees trees = Trees.instance(compiled.task);

        return compiled.cursor
                .flatMap(new Hovers(compiled.task))
                .map(text -> new Hover(Collections.singletonList(Either.forLeft(text)), null));
    }

    private final Trees trees;

    private Hovers(JavacTask task) {
        trees = Trees.instance(task);
    }

    @Override
    public Optional<String> apply(TreePath path) {
        Element element = trees.getElement(path);

        if (element == null)
            return Optional.empty();

        Optional<TypeMirror> type = type(element);

        switch (element.getKind()) {
            case PACKAGE:
                return Optional.of("package " + element.getSimpleName());
            case ENUM:
                return Optional.of("enum " + element.getSimpleName());
            case CLASS:
                return Optional.of("class " + element.getSimpleName());
            case ANNOTATION_TYPE:
                return Optional.of("@interface " + element.getSimpleName());
            case INTERFACE:
                return Optional.of("interface " + element.getSimpleName());
            case METHOD:
                return Optional.of(methodSignature((Symbol.MethodSymbol) element));
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

    // TODO this uses non-public APIs
    private static String methodSignature(Symbol.MethodSymbol e) {
        String name = e.getSimpleName().toString();
        boolean varargs = e.isVarArgs();
        StringJoiner params = new StringJoiner(", ");

        com.sun.tools.javac.util.List<Symbol.VarSymbol> parameters = e.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            Symbol.VarSymbol p = parameters.get(i);
            String pName = shortName(p, varargs && i == parameters.size() - 1);

            params.add(pName);
        }

        String signature = name + "(" + params + ")";

        if (!e.getThrownTypes().isEmpty()) {
            StringJoiner thrown = new StringJoiner(", ");

            for (Type t : e.getThrownTypes())
                thrown.add(ShortTypePrinter.print(t));

            signature += " throws " + thrown;
        }

        return signature;
    }

    private static String shortName(Symbol.VarSymbol p, boolean varargs) {
        Type type = p.type;

        if (varargs) {
            Type.ArrayType array = (Type.ArrayType) type;

            type = array.getComponentType();
        }

        String acc = shortTypeName(type);
        String name = p.name.toString();

        if (varargs)
            acc += "...";

        if (!name.matches("arg\\d+"))
            acc += " " + name;

        return acc;
    }

    private static String shortTypeName(Type type) {
        return ShortTypePrinter.print(type);
    }

}
