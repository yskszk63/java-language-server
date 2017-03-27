package org.javacs;

import com.google.common.collect.ImmutableList;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.comp.GetStaticLevel;
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
    private final Name thisName, superName;

    private Completions(JavacTask task) {
        this.task = task;
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.thisName = task.getElements().getName("this");
        this.superName = task.getElements().getName("super");
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
        else if (leaf instanceof NewClassTree) {
            return constructors(scope);
        }
        else if (leaf instanceof IdentifierTree) {
            return allSymbols(scope);
        }
        else return Stream.empty();
    }

    /**
     * Suggest all accessible members of expression
     */
    private Stream<CompletionItem> completeMembers(TreePath expression, Scope from) {
        Element element = trees.getElement(expression);
        TypeMirror type = trees.getTypeMirror(expression);

        if (element == null || type == null)
            return Stream.empty();

        boolean isStatic = isTypeSymbol(element);
        List<? extends Element> all = members(type);

        Stream<CompletionItem> filter = all.stream()
                .filter(e -> isAccessible(e, from))
                .filter(e -> e.getModifiers().contains(Modifier.STATIC) == isStatic)
                .flatMap(e -> completionItem(e, distance(e, from)));

        if (isStatic) {
            filter = Stream.concat(
                    Stream.of(namedProperty("class")),
                    filter
            );
        }

        List<CompletionItem> parentClassThis = parentClassScope(from, element)
                .filter(classScope -> !isStaticScope(classScope, from) && !isStaticMethod(from))
                .map(this::thisAndSuper)
                .orElseGet(Collections::emptyList);

        filter = Stream.concat(parentClassThis.stream(), filter);

        return filter;
    }

    /**
     * If element is the TypeElement of a parent class of from, return its scope
     */
    private Optional<Scope> parentClassScope(Scope scope, Element element) {
        Scope foundClassScope = null;

        while (scope != null) {
            if (element.equals(scope.getEnclosingClass()))
                foundClassScope = scope;

            scope = scope.getEnclosingScope();
        }

        return Optional.ofNullable(foundClassScope);
    }

    private List<CompletionItem> thisAndSuper(Scope classScope) {
        return ImmutableList.of(namedProperty("this"), namedProperty("super"));
    }

    /**
     * All members of element, if it is TypeElement
     */
    private List<? extends Element> members(TypeMirror expressionType) {
        if (expressionType == null)
            return Collections.emptyList();

        return typeElement(expressionType)
                .map(elements::getAllMembers)
                .orElseGet(Collections::emptyList);
    }

    /**
     * Suggest a simple completion 'name'
     */
    private static CompletionItem namedProperty(String name) {
        CompletionItem item = new CompletionItem();

        item.setKind(CompletionItemKind.Property);
        item.setLabel(name);
        item.setInsertText(name);
        item.setSortText("0/" + name);

        return item;
    }

    private boolean isTypeSymbol(Element element) {
        if (element == null)
            return false;

        switch (element.getKind()) {
            case CLASS:
            case INTERFACE:
            case ENUM:
                return true;
            default:
                return false;
        }
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

    private Stream<CompletionItem> constructors(Scope start) {
        // TODO autocomplete classes that are imported *anywhere* on the source path and sort them second
        return findAllSymbols(start).stream()
                .filter(this::isTypeSymbol)
                .filter(e -> isAccessible(e, start))
                .flatMap(this::explodeConstructors)
                .flatMap(constructor -> completionItem(constructor, 0));
    }

    private Stream<ExecutableElement> explodeConstructors(Element element) {
        List<? extends Element> all = members(element.asType());

        return all.stream().flatMap(this::asConstructor);
    }

    private Stream<ExecutableElement> asConstructor(Element element) {
        if (element.getKind() == ElementKind.CONSTRUCTOR)
            return Stream.of((ExecutableElement) element);
        else
            return Stream.empty();
    }

    /**
     * Suggest all completions that are visible from scope
     */
    private Stream<CompletionItem> allSymbols(Scope scope) {
        Set<Element> all = findAllSymbols(scope);

        return all.stream()
                .filter(e -> isAccessible(e, scope))
                .flatMap(e -> completionItem(e, distance(e, scope)));
    }

    private Set<Element> findAllSymbols(Scope scope) {
        Set<Element> all = doAllSymbols(scope);

        // Add all members of this package
        packageOf(scope.getEnclosingClass())
                .map(PackageElement::getEnclosedElements)
                .ifPresent(all::addAll);

        return all;
    }

    private Optional<PackageElement> packageOf(Element enclosing) {
        return Optional.ofNullable(elements.getPackageOf(enclosing));
    }

    /**
     * Recursively check each enclosing scope for members that are visible from the starting scope.
     *
     * Visibility takes into consideration static / virtual, but not accessibility modifiers.
     * We'll deal with those later.
     */
    private Set<Element> doAllSymbols(final Scope start) {
        Set<Element> acc = new LinkedHashSet<>();
        Scope scope = start;

        while (scope != null) {
            for (Element each : scope.getLocalElements()) {
                // Don't include 'this' or 'super' except in the closest scope
                boolean skipThis = scope != start || isStaticMethod(scope);

                if (skipThis && isThisOrSuper(each))
                    continue;

                acc.add(each);
            }

            // If this is the scope of a class, add all accessible members of the class
            if (scope.getEnclosingClass() != null) {
                boolean isStatic = isStaticScope(scope, start) || isStaticMethod(scope);

                for (Element each : elements.getAllMembers(scope.getEnclosingClass())) {
                    // If this is a virtual scope, we have access to all members
                    // If this is a static scope, we only have access to static members
                    if (!isStatic || each.getModifiers().contains(Modifier.STATIC))
                        acc.add(each);
                }
            }

            scope = scope.getEnclosingScope();
        }

        return acc;
    }

    private boolean isStaticMethod(Scope scope) {
        return scope.getEnclosingMethod() != null && scope.getEnclosingMethod().getModifiers().contains(Modifier.STATIC);
    }

    private Optional<Scope> classScope(TypeElement classElement) {
        if (classElement == null)
            return Optional.empty();

        TreePath classPath = trees.getPath(classElement);

        if (classPath == null)
            return Optional.empty();

        return Optional.ofNullable(trees.getScope(classPath));
    }

    private boolean isStaticScope(Scope toOuter, Scope fromInner) {
        if (!isParentScope(toOuter, fromInner))
            return true;

        // It sucks that we have to break through the public API but I can't find any other way to get this info
        JavacScope inner = (JavacScope) fromInner;
        JavacScope outer = (JavacScope) toOuter;

        if (outer == null)
            return false;

        // TODO is countStaticClasses enough?
        int outerStaticLevel = GetStaticLevel.getStaticLevel(outer.getEnv().info) + countStatics(outer);
        int innerStaticLevel = GetStaticLevel.getStaticLevel(inner.getEnv().info) + countStatics(inner);

        return outerStaticLevel < innerStaticLevel;
    }

    private int countStatics(Scope scope) {
        int count = 0;
        Element c = scope.getEnclosingMethod() != null ? scope.getEnclosingMethod() : scope.getEnclosingClass();

        while (c != null) {
            if (c.getModifiers().contains(Modifier.STATIC))
                count++;

            c = c.getEnclosingElement();
        }

        return count;
    }

    private boolean isParentScope(final Scope toOuter, final Scope fromInner) {
        Scope next = fromInner;

        while (next != null) {
            if (next.equals(toOuter))
                return true;
            else
                next = next.getEnclosingScope();
        }

        return false;
    }

    private boolean isThisOrSuper(Element each) {
        Name name = each.getSimpleName();

        return name.equals(thisName) || name.equals(superName);
    }

    private int distance(Element e, Scope scope) {
        // TODO
        return 0;
    }

    private Stream<CompletionItem> completionItem(Element e, int distance) {
        String name = e.getKind() == ElementKind.CONSTRUCTOR ? Hovers.constructorName((ExecutableElement) e) : e.getSimpleName().toString();
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
                item.setDetail(ShortTypePrinter.print(e.asType()));
                docstring(e).ifPresent(item::setDocumentation);
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
                item.setLabel(Hovers.methodSignature(method));
                item.setDetail(ShortTypePrinter.print(method.getReturnType()));
                docstring(e).ifPresent(item::setDocumentation);
                item.setInsertText(name); // TODO
                item.setSortText(sortText);
                item.setFilterText(name);

                return Stream.of(item);
            }
            case CONSTRUCTOR: {
                ExecutableElement method = (ExecutableElement) e;
                CompletionItem item = new CompletionItem();
                String insertText = name;

                if (!method.getTypeParameters().isEmpty())
                    insertText += "<>";

                item.setKind(CompletionItemKind.Constructor);
                item.setLabel(Hovers.methodSignature(method));
                docstring(e).ifPresent(item::setDocumentation);
                item.setInsertText(insertText);
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
        return Optional.ofNullable(elements.getDocComment(e));
    }
}
