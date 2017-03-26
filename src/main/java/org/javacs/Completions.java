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
        else if (leaf instanceof IdentifierTree) {
            return membersOfScope(scope);
        }
        else return Stream.empty();
    }

    /**
     * Suggest all accessible members of expression
     */
    private Stream<CompletionItem> completeMembers(TreePath expression, Scope from) {
        Element element = trees.getElement(expression);

        if (element == null)
            return Stream.empty();

        boolean isStatic = isTypeSymbol(element.getKind());
        List<? extends Element> all = members(element);

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

        if (isEnclosingClass(element, from)) {
            filter = Stream.concat(
                    Stream.of(namedProperty("this"), namedProperty("super")),
                    filter
            );
        }

        return filter;
    }

    /**
     * Is element an enclosing class of scope, meaning element.this is accessible?
     */
    private boolean isEnclosingClass(Element element, Scope scope) {
        if (scope == null)
            return false;
        // If this is the scope of a static method, for example
        //
        //   class Foo {
        //     static void test() { [scope] }
        //   }
        //
        // then Foo.this is not accessible
        else if (isStaticMethodScope(scope))
            return false;
        else if (element.equals(scope.getEnclosingClass()))
            return true;
        // If this is the scope of a static class, for example
        //
        //    class Outer {
        //      static class Inner {
        //        void test() { [scope] }
        //      }
        //    }
        //
        // then Outer.this is not accessible
        else if (isStaticClassScope(scope))
            return false;
        else
            return isEnclosingClass(element, scope.getEnclosingScope());
    }

    /**
     * All members of element, if it is TypeElement
     */
    private List<? extends Element> members(Element element) {
        if (element == null)
            return Collections.emptyList();

        TypeMirror expressionType = element.asType();

        return typeElement(expressionType)
                .map(e -> elements.getAllMembers(e))
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

    private Optional<TypeElement> typeElement(TypeMirror type) {
        if (type instanceof DeclaredType) {
            DeclaredType declared = (DeclaredType) type;
            Element element = declared.asElement();

            if (element instanceof TypeElement)
                return Optional.of((TypeElement) element);
        }

        return Optional.empty();
    }

    /**
     * Suggest all completions that are visible from scope
     */
    private Stream<CompletionItem> membersOfScope(Scope scope) {
        Set<Element> all = new LinkedHashSet<>();

        // Add 'this' and 'super' once
        scope.getLocalElements().forEach(all::add);

        findScopeMembers(scope, false, all);

        return all.stream()
                .filter(e -> isAccessible(e, scope))
                .flatMap(e -> completionItem(e, distance(e, scope)));
    }

    /**
     * Recursively check each enclosing scope for members that are visible from the starting scope.
     *
     * Visibility takes into consideration static / virtual, but not accessibility modifiers.
     * We'll deal with those later.
     */
    private void findScopeMembers(Scope scope, boolean isStatic, Set<Element> acc) {
        if (scope == null)
            return;

        for (Element each : scope.getLocalElements()) {
            // Don't include 'this' or 'super'
            // It will be done ONCE by membersOfScope
            if (!isThisOrSuper(each))
                acc.add(each);
        }

        // If this is the scope of a static method, it won't have access to virtual members of any enclosing scopes
        if (isStaticMethodScope(scope))
            isStatic = true;

        // If this is the scope of a class, add all accessible members of the class
        if (scope.getEnclosingClass() != null) {
            for (Element each : elements.getAllMembers(scope.getEnclosingClass())) {
                // If this is a virtual scope, we have access to all members
                // If this is a static scope, we only have access to static members
                if (!isStatic || each.getModifiers().contains(Modifier.STATIC))
                    acc.add(each);
            }
        }

        // If this is the scope of a static class, it won't have access to virtual members of any enclosing scopes
        if (isStaticClassScope(scope))
            isStatic = true;

        findScopeMembers(scope.getEnclosingScope(), isStatic, acc);
    }

    private boolean isStaticClassScope(Scope scope) {
        return scope.getEnclosingClass() != null && scope.getEnclosingClass().getModifiers().contains(Modifier.STATIC);
    }

    private boolean isStaticMethodScope(Scope scope) {
        return scope.getEnclosingMethod() != null && scope.getEnclosingMethod().getModifiers().contains(Modifier.STATIC);
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
                item.setLabel(Hovers.methodSignature(method));
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
