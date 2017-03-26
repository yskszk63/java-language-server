package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class Completions implements Function<TreePath, Stream<CompletionItem>> {

    public static Stream<CompletionItem> at(FocusedResult compiled) {
        return compiled.cursor
                .map(new Completions(compiled.task))
                .orElseGet(Stream::empty);
    }

    private final JavacTask task;
    private final Trees trees;
    private final Elements elements;

    private Completions(JavacTask task) {
        this.task = task;
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
    }

    @Override
    public Stream<CompletionItem> apply(TreePath path) {
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
        Element element = trees.getElement(expression);

        if (element == null)
            return Stream.empty();

        boolean isStatic = isTypeSymbol(element.getKind());
        TypeMirror expressionType = trees.getTypeMirror(expression);
        List<? extends Element> all = typeElement(expressionType)
                .map(e -> elements.getAllMembers(e))
                .orElseGet(Collections::emptyList);
        Stream<CompletionItem> filter = all.stream()
                .filter(e -> isAccessible(e, from))
                .filter(e -> isStatic(e) == isStatic)
                .flatMap(e -> completionItem(e, distance(e, from)));

        if (isStatic)
            filter = Stream.concat(Stream.of(dotClass()), filter);

        return filter;
    }

    private static CompletionItem dotClass() {
        CompletionItem item = new CompletionItem();

        item.setKind(CompletionItemKind.Class);
        item.setLabel("class");
        item.setInsertText("class");
        item.setSortText("0/class");

        return item;
    }

    private boolean isTypeSymbol(ElementKind kind) {
        switch (kind) {
            case CLASS:
            case INTERFACE:
            case ENUM:
                return true;
            default:
                return false;
        }
    }

    private boolean isStatic(Element e) {
        return e.getModifiers().contains(Modifier.STATIC);
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
                .filter(e -> isAccessible(e, scope))
                .flatMap(e -> completionItem(e, distance(e, scope)));
    }

    private int distance(Element e, Scope scope) {
        // TODO
        return 0;
    }

    /*


    private Stream<Scope> scopes(Scope start) {
        Map<Scope, Boolean> scopes = new LinkedHashMap<>();

        findScopes(start, false, scopes);

        return scopes.stream();
    }

    private void findScopes(Scope scope, boolean isStatic, Map<Scope, Boolean> scopes) {
        if (scope == null || scopes.containsKey(scope))
            return;

        isStatic = isStatic || isStaticScope(scope);

        scopes.put(scope, isStatic);

        findScopes(scope.getEnclosingScope(), isStatic, scopes);
    }

    private boolean isStaticScope(Scope scope) {
        if (scope.getEnclosingMethod() != null && scope.getEnclosingMethod().getModifiers().contains(Modifier.STATIC))
            return true;
        else if (scope.getEnclosingClass() != null && scope.getEnclosingClass().getModifiers().contains(Modifier.STATIC))
            return true;
        else
            return false;
    }
     */

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

    private Stream<CompletionItem> completionItem(Element e, int distance) {
        String name = e.getSimpleName().toString();
        String sortText = distance + "/" + name;
        
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
        TypeMirror enclosing = e.getEnclosingElement().asType();

        if (enclosing instanceof DeclaredType)
            return trees.isAccessible(scope, e, (DeclaredType) enclosing);
        else
            return true;
    }

    private Optional<String> docstring(Element e) {
        TreePath path = trees.getPath(e);

        if (path == null)
            return Optional.empty();
        else
            return Optional.ofNullable(trees.getDocComment(path));
    }
}
