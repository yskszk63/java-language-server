package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.*;
import java.util.stream.Stream;

public class Completions {
    private final JavacTask task;
    private final Trees trees;
    private final Elements elements;

    public Completions(JavacTask task) {
        this.task = task;
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
    }

    public Stream<CompletionItem> at(TreePath path) {
        Tree leaf = path.getLeaf();
        Scope scope = trees.getScope(path);

        if (leaf instanceof MemberSelectTree) {
            MemberSelectTree select = (MemberSelectTree) leaf;
            TreePath expressionPath = new TreePath(path.getParentPath(), select.getExpression());

            return completeMembers(expressionPath, scope);
        }
        else if (leaf instanceof MemberReferenceTree) {
            MemberReferenceTree select = (MemberReferenceTree) leaf;
            TreePath expressionPath = new TreePath(path.getParentPath(), select.getQualifierExpression());

            return completeMembers(expressionPath, scope);
        }
        else if (leaf instanceof IdentifierTree) {
            return membersOfScope(scope);
        }
        else return Stream.empty();
    }

    private Stream<CompletionItem> completeMembers(TreePath expression, Scope from) {
        return membersOfExpression(expression, from)
                .flatMap(e -> completionItem(e, from));
    }

    private Stream<? extends Element> membersOfExpression(TreePath expression, Scope from) {
        TypeMirror expressionType = trees.getTypeMirror(expression);

        return typeElement(expressionType)
                .map(element -> membersOfType(element, from))
                .orElseGet(Stream::empty);
    }

    private Stream<? extends Element> membersOfType(TypeElement of, Scope from) {
        List<? extends Element> found = elements.getAllMembers(of);

        return found.stream().filter(e -> isAccessible(e, from));
    }

    private Optional<TypeElement> typeElement(TypeMirror type) {
        if (type instanceof DeclaredType) {
            DeclaredType declared = (DeclaredType) type;
            Element element = declared.asElement();

            if (element instanceof TypeElement)
                return Optional.of((TypeElement) element);
        }

        return Optional.empty();
    }

    private Stream<CompletionItem> membersOfScope(Scope scope) {
        return scopes(scope)
                .flatMap(this::elements)
                .flatMap(e -> completionItem(e, scope));
    }

    private Stream<Scope> scopes(Scope start) {
        Set<Scope> scopes = new LinkedHashSet<>();

        findScopes(start, scopes);

        return scopes.stream();
    }

    private void findScopes(Scope scope, Set<Scope> scopes) {
        if (scope == null || scopes.contains(scope))
            return;

        scopes.add(scope);

        findScopes(scope.getEnclosingScope(), scopes);
    }

    private Stream<Element> elements(Scope scope) {
        Set<Element> elements = new HashSet<>();

        findElements(scope, elements);

        return elements.stream();
    }

    private void findElements(Scope scope, Set<Element> elements) {
        if (scope == null)
            return;

        scope.getLocalElements().forEach(elements::add);

        TypeElement enclosingClass = scope.getEnclosingClass();

        if (enclosingClass != null)
            enclosingClass.getEnclosedElements().forEach(elements::add);

        findElements(scope.getEnclosingScope(), elements);
    }

    private Stream<CompletionItem> completionItem(Element e, Scope scope) {
        if (!isAccessible(e, scope))
            return Stream.empty();

        String name = e.getSimpleName().toString();
        String sortText = distance(e, scope) + "/" + name;
        
        switch (e.getKind()) {
            case PACKAGE:
                return Stream.empty();
            case ENUM:
            case CLASS: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Class);
                item.setLabel(name);
                item.setInsertText(name);
                item.setSortText(sortText);

                return Stream.of(item);
            }
            case ANNOTATION_TYPE:
            case INTERFACE:
            case TYPE_PARAMETER: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Reference);
                item.setLabel(name);
                item.setInsertText(name);
                item.setSortText(sortText);

                return Stream.of(item);
            }
            case ENUM_CONSTANT: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Enum);
                item.setLabel(name);
                item.setDetail(e.getEnclosingElement().getSimpleName().toString());
                item.setInsertText(name);
                item.setSortText(sortText);

                return Stream.of(item);
            }
            case FIELD: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Property);
                item.setLabel(name);
                Optional.of(ShortTypePrinter.print(e.asType())).map(CharSequence::toString).ifPresent(item::setDetail);
                item.setInsertText(name);
                item.setSortText(sortText);

                return Stream.of(item);
            }
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Variable);
                item.setLabel(name);
                item.setInsertText(name);
                item.setSortText(sortText);

                return Stream.of(item);
            }
            case METHOD: {
                ExecutableElement method = (ExecutableElement) e;
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Method);
                item.setLabel(name);
                item.setDetail(ShortTypePrinter.print(method.getReturnType()));
                docstring(method).ifPresent(item::setDocumentation);
                item.setInsertText(name); // TODO
                item.setSortText(sortText);
                item.setFilterText(name);

                return Stream.of(item);
            }
            case CONSTRUCTOR: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Constructor);
                item.setLabel(name);
                item.setInsertText(name);
                item.setSortText(sortText);
                item.setFilterText(name);

                return Stream.of(item);
            }
            case STATIC_INIT:
            case INSTANCE_INIT:
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                // Nothing user-enterable
                // Nothing user-enterable
                return Stream.empty();
        }
    }

    private boolean isAccessible(Element e, Scope scope) {
        return !(e instanceof TypeElement && !trees.isAccessible(scope, (TypeElement) e));
    }

    private int distance(Element e, Scope scope) {
        // TODO
        return 0;
    }

    private Optional<String> docstring(Element e) {
        TreePath path = trees.getPath(e);

        if (path == null)
            return Optional.empty();
        else
            return Optional.ofNullable(trees.getDocComment(path));
    }
}
