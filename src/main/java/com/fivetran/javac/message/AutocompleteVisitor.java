package com.fivetran.javac.message;

import com.fivetran.javac.BridgeExpressionScanner;
import com.fivetran.javac.BridgeTypeVisitor;
import com.google.common.base.Joiner;
import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class AutocompleteVisitor extends CursorScanner {
    private static final Logger LOG = Logger.getLogger("");
    public final Set<AutocompleteSuggestion> suggestions = new LinkedHashSet<>();

    public AutocompleteVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    /**
     * [expression].[identifier]
     */
    @Override
    public void visitSelect(JCTree.JCFieldAccess node) {
        // If expression contains cursor, no autocomplete
        JCTree.JCExpression expression = node.getExpression();

        if (containsCursor(expression))
            super.visitSelect(node);
        else {
            JavacTrees trees = JavacTrees.instance(context);
            TreePath pathToExpression = new TreePath(path, expression);
            TypeMirror type = trees.getTypeMirror(pathToExpression);

            if (type == null)
                LOG.warning("No type for " + Joiner.on("/").join(pathToExpression));
            else if (isClassReference(expression)) {
                suggestions.add(new AutocompleteSuggestion("class", "class", AutocompleteSuggestion.Type.Property));

                type.accept(new CollectStatics(), null);
            }
            else
                type.accept(new CollectVirtuals(), null);
        }
    }

    private boolean isClassReference(ExpressionTree expression) {
        return expression instanceof JCTree.JCIdent &&
               ((JCTree.JCIdent) expression).sym instanceof Symbol.ClassSymbol;
    }

    @Override
    public void visitReference(JCTree.JCMemberReference node) {
        // If expression contains cursor, no autocomplete
        JCTree.JCExpression expression = node.getQualifierExpression();

        if (containsCursor(expression))
            super.visitReference(node);
        else {
            JavacTrees trees = JavacTrees.instance(context);
            TreePath pathToExpression = new TreePath(path, expression);
            TypeMirror type = trees.getTypeMirror(pathToExpression);

            if (type == null)
                LOG.warning("No type for " + Joiner.on("/").join(pathToExpression));
            else if (isClassReference(expression)) {
                suggestions.add(new AutocompleteSuggestion("new", "new", AutocompleteSuggestion.Type.Method));

                type.accept(new CollectStatics(), null);
            }
            else
                type.accept(new CollectVirtuals(), null);
        }
    }

    @Override
    public void visitIdent(JCTree.JCIdent node) {
        super.visitIdent(node);

        JavacScope scope = JavacTrees.instance(context).getScope(path);

        while (scope != null) {
            LOG.info(Joiner.on(", ").join(scope.getLocalElements()));

            for (Element e : scope.getLocalElements())
                addElement(e);
            // TODO add to suggestions

            scope = scope.getEnclosingScope();
        }
    }

    private void addElement(Element e) {
        String name = e.getSimpleName().toString();

        switch (e.getKind()) {
            case PACKAGE:
                break;
            case ENUM:
            case CLASS:
            case ANNOTATION_TYPE:
            case INTERFACE:
            case TYPE_PARAMETER:
                suggestions.add(new AutocompleteSuggestion(name, name, AutocompleteSuggestion.Type.Interface));

                break;
            case ENUM_CONSTANT:
                addEnumConstant(e);

                break;
            case FIELD:
                addField(e);

                break;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
                suggestions.add(new AutocompleteSuggestion(name, name, AutocompleteSuggestion.Type.Variable));

                break;
            case METHOD:
                addMethod((Symbol.MethodSymbol) e);

                break;
            case CONSTRUCTOR:
                // TODO
                break;
            case STATIC_INIT:
                // Nothing user-enterable
                break;
            case INSTANCE_INIT:
                // Nothing user-enterable
                break;
            case OTHER:
                break;
            case RESOURCE_VARIABLE:
                break;
        }
    }

    private void addEnumConstant(Element e) {
        String name = e.getSimpleName().toString();
        AutocompleteSuggestion suggestion = new AutocompleteSuggestion(name, name, AutocompleteSuggestion.Type.Enum);

        suggestion.detail = Optional.of(e.getEnclosingElement().getSimpleName().toString());

        suggestions.add(suggestion);
    }

    private void addMethod(Symbol.MethodSymbol e) {
        String name = e.getSimpleName().toString();
        AutocompleteSuggestion suggestion = new AutocompleteSuggestion(name, name, AutocompleteSuggestion.Type.Method);

        suggestion.detail = Optional.of(e.getEnclosingElement().getSimpleName().toString());

        suggestions.add(suggestion);
    }

    private void addField(Element e) {
        String name = e.getSimpleName().toString();
        AutocompleteSuggestion suggestion = new AutocompleteSuggestion(name, name, AutocompleteSuggestion.Type.Property);

        suggestion.detail = Optional.of(e.getEnclosingElement().getSimpleName().toString());

        suggestions.add(suggestion);
    }

    private class CollectStatics extends BridgeTypeVisitor {

        @Override
        public void visitDeclared(DeclaredType t) {
            TypeElement typeElement = (TypeElement) t.asElement();
            List<? extends Element> members = JavacElements.instance(context).getAllMembers(typeElement);

            for (Element e : members) {
                switch (e.getKind()) {
                    case FIELD:
                        Symbol.VarSymbol field = (Symbol.VarSymbol) e;

                        if (field.isStatic())
                            addField(field);

                        break;
                    case METHOD:
                        Symbol.MethodSymbol method = (Symbol.MethodSymbol) e;

                        if (method.isStatic())
                            addMethod(method);

                        break;
                }
            }
        }
    }

    private class CollectVirtuals extends BridgeTypeVisitor {
        @Override
        public void visitArray(ArrayType t) {
            // Array types just have 'length'
            AutocompleteSuggestion length = new AutocompleteSuggestion("length",
                                                                       "length",
                                                                       AutocompleteSuggestion.Type.Property);

            suggestions.add(length);
        }

        @Override
        public void visitTypeVariable(TypeVariable t) {
            visit(t.getUpperBound());
        }

        @Override
        public void visitDeclared(DeclaredType t) {
            TypeElement typeElement = (TypeElement) t.asElement();
            List<? extends Element> members = JavacElements.instance(context).getAllMembers(typeElement);

            for (Element e : members) {
                switch (e.getKind()) {
                    case FIELD:
                        Symbol.VarSymbol field = (Symbol.VarSymbol) e;

                        if (!field.isStatic())
                            addField(field);

                        break;
                    case METHOD:
                        Symbol.MethodSymbol method = (Symbol.MethodSymbol) e;

                        if (!method.isStatic())
                            addMethod(method);

                        break;
                }
            }
        }

        @Override
        public void visitWildcard(WildcardType t) {
            visit(t.getExtendsBound());
        }
    }
}
