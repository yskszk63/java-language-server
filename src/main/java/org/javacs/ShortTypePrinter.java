package org.javacs;

import javax.lang.model.type.*;
import javax.lang.model.util.AbstractTypeVisitor8;
import java.util.stream.Collectors;

public class ShortTypePrinter extends AbstractTypeVisitor8<String, Void> {
    private ShortTypePrinter() {

    }

    public static String print(TypeMirror type) {
        return type.accept(new ShortTypePrinter(), null);
    }

    @Override
    public String visitIntersection(IntersectionType t, Void aVoid) {
        return t.getBounds().stream().map(ShortTypePrinter::print).collect(Collectors.joining(" & "));
    }

    @Override
    public String visitUnion(UnionType t, Void aVoid) {
        return t.getAlternatives().stream().map(ShortTypePrinter::print).collect(Collectors.joining(" | "));
    }

    @Override
    public String visitPrimitive(PrimitiveType t, Void aVoid) {
        return t.toString();
    }

    @Override
    public String visitNull(NullType t, Void aVoid) {
        return t.toString();
    }

    @Override
    public String visitArray(ArrayType t, Void aVoid) {
        return print(t.getComponentType()) + "[]";
    }

    @Override
    public String visitDeclared(DeclaredType t, Void aVoid) {
        String result = "";

        if (t.getEnclosingType().getKind() == TypeKind.DECLARED)
            result += print(t.getEnclosingType()) + ".";

        result += t.asElement().getSimpleName().toString();

        if (!t.getTypeArguments().isEmpty()) {
            String params = t.getTypeArguments()
                              .stream()
                              .map(ShortTypePrinter::print)
                              .collect(Collectors.joining(", "));

            result += "<" + params + ">";
        }

        return result;
    }

    @Override
    public String visitError(ErrorType t, Void aVoid) {
        return "???";
    }

    @Override
    public String visitTypeVariable(TypeVariable t, Void aVoid) {
        String result = t.asElement().toString();
        TypeMirror upper = t.getUpperBound();

        if (!upper.toString().equals("java.lang.Object"))
            result += " extends " + print(upper);

        return result;
    }

    @Override
    public String visitWildcard(WildcardType t, Void aVoid) {
        String result = "?";

        if (t.getSuperBound() != null)
            result += " super " + print(t.getSuperBound());

        if (t.getExtendsBound() != null)
            result += " extends " + print(t.getExtendsBound());

        return result;
    }

    @Override
    public String visitExecutable(ExecutableType t, Void aVoid) {
        return t.toString();
    }

    @Override
    public String visitNoType(NoType t, Void aVoid) {
        return t.toString();
    }
}
