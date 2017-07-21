package org.javacs;

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;

class ClassApiVisitor extends ElementScanner8<Void, Void> {
    // The pubapi is stored here.
    List<String> api = new LinkedList<String>();
    // Indentation level.
    int indent = 0;

    String depth(int l) {
        return "________________________________".substring(0, l);
    }

    public String construct(TypeElement e) {
        visit(e);

        Collections.sort(api);

        return Joiner.on("\n").join(api);
    }

    @Override
    public Void visitType(TypeElement e, Void p) {
        if (e.getModifiers().contains(Modifier.PUBLIC)
            || e.getModifiers().contains(Modifier.PROTECTED))
        {
            api.add(depth(indent) + "!TYPE " + e.getQualifiedName());
            indent += 2;
            Void v = super.visitType(e, p);
            indent -= 2;
            return v;
        }
        return null;
    }

    @Override
    public Void visitVariable(VariableElement e, Void p) {
        if (e.getModifiers().contains(Modifier.PUBLIC)
            || e.getModifiers().contains(Modifier.PROTECTED)) {
            api.add(depth(indent)+"VAR "+makeVariableString(e));
        }
        // Safe to not recurse here, because the only thing
        // to visit here is the constructor of a variable declaration.
        // If it happens to contain an anonymous inner class (which it might)
        // then this class is never visible outside of the package anyway, so
        // we are allowed to ignore it here.
        return null;
    }

    @Override
    public Void visitExecutable(ExecutableElement e, Void p) {
        if (e.getModifiers().contains(Modifier.PUBLIC)
            || e.getModifiers().contains(Modifier.PROTECTED)) {
            api.add(depth(indent)+"METHOD "+makeMethodString(e));
        }
        return null;
    }

    /**
     * Creates a String representation of a method element with everything
     * necessary to track all public aspects of it in an API.
     * @param e Element to create String for.
     * @return String representation of element.
     */
    protected String makeMethodString(ExecutableElement e) {
        StringBuilder result = new StringBuilder();
        for (Modifier modifier : e.getModifiers()) {
            result.append(modifier.toString());
            result.append(" ");
        }
        result.append(e.getReturnType().toString());
        result.append(" ");
        result.append(e.toString());

        List<? extends TypeMirror> thrownTypes = e.getThrownTypes();
        if (!thrownTypes.isEmpty()) {
            result.append(" throws ");
            for (Iterator<? extends TypeMirror> iterator = thrownTypes
                    .iterator(); iterator.hasNext();) {
                TypeMirror typeMirror = iterator.next();
                result.append(typeMirror.toString());
                if (iterator.hasNext()) {
                    result.append(", ");
                }
            }
        }
        return result.toString();
    }

    /**
     * Creates a String representation of a variable element with everything
     * necessary to track all public aspects of it in an API.
     * @param e Element to create String for.
     * @return String representation of element.
     */
    protected String makeVariableString(VariableElement e) {
        StringBuilder result = new StringBuilder();
        for (Modifier modifier : e.getModifiers()) {
            result.append(modifier.toString());
            result.append(" ");
        }
        result.append(e.asType().toString());
        result.append(" ");
        result.append(e.toString());
        Object value = e.getConstantValue();
        if (value != null) {
            result.append(" = ");
            if (e.asType().toString().equals("char")) {
                int v = (int)value.toString().charAt(0);
                result.append("'\\u"+Integer.toString(v,16)+"'");
            } else {
                result.append(value.toString());
            }
        }
        return result.toString();
    }
}