package org.javacs;

import com.google.common.reflect.ClassPath;
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
import java.util.stream.StreamSupport;

public class Completions implements Function<TreePath, Stream<CompletionItem>> {

    public static Stream<CompletionItem> at(FocusedResult compiled) {
        return compiled.cursor
                .map(new Completions(compiled.task, compiled.classPath, compiled.sourcePath))
                .orElseGet(Stream::empty);
    }

    private final JavacTask task;
    private final ClassPath classPath;
    private final SymbolIndex sourcePath;
    private final Trees trees;
    private final Elements elements;
    private final Name thisName, superName;

    private Completions(JavacTask task, ClassPath classPath, SymbolIndex sourcePath) {
        this.task = task;
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.thisName = task.getElements().getName("this");
        this.superName = task.getElements().getName("super");
        this.classPath = classPath;
        this.sourcePath = sourcePath;
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

        if (element instanceof PackageElement) {
            PackageElement packageElement = (PackageElement) element;

            return completeImport(packageElement.getQualifiedName().toString(), from);
        }

        TypeMirror type = trees.getTypeMirror(expression);

        if (element == null || type == null)
            return Stream.empty();

        boolean isStatic = isTypeSymbol(element);
        List<? extends Element> all = members(type);

        Stream<CompletionItem> filter = all.stream()
                .filter(e -> isAccessible(e, from))
                .filter(e -> e.getModifiers().contains(Modifier.STATIC) == isStatic)
                .flatMap(this::completionItem);

        if (isStatic) {
            filter = Stream.concat(
                    Stream.of(namedProperty("class")),
                    filter
            );

            if (thisScopes(from).contains(element)) {
                filter = Stream.concat(
                        Stream.of(namedProperty("this"), namedProperty("super")),
                        filter
                );
            }
        }

        return filter;
    }

    private Optional<String> parentPackageName(Element enclosingElement) {
        if (enclosingElement instanceof PackageElement) {
            PackageElement enclosingPackage = (PackageElement) enclosingElement;

            return Optional.of(enclosingPackage.getQualifiedName().toString());
        }
        else return Optional.empty();
    }

    private Stream<CompletionItem> completeImport(String parentPackage, Scope from) {
        return Stream.concat(
                classPath.getTopLevelClasses().stream()
                        .filter(info -> info.getPackageName().startsWith(parentPackage))
                        .filter(info -> !isAlreadyImported(info.getPackageName(), info.getSimpleName()))
                        .filter(info -> isAccessible(classElement(info.getPackageName(), info.getSimpleName()), from))
                        .map(info -> completeFullyQualifiedClassName(info.getPackageName(), info.getSimpleName(), parentPackage)),
                sourcePath.allSymbols(ElementKind.CLASS)
                        .filter(info -> info.getContainerName().startsWith(parentPackage))
                        .filter(info -> !isAlreadyImported(info.getContainerName(), info.getName()))
                        .filter(info -> isTopLevelClass(info.getContainerName(), info.getName()))
                        .filter(info -> isAccessible(classElement(info.getContainerName(), info.getName()), from))
                        .map(info -> completeFullyQualifiedClassName(info.getContainerName(), info.getName(), parentPackage))
        );
    }

    private boolean isAlreadyImported(String packageName, String className) {
        return false; // TODO
    }

    private boolean isTopLevelClass(String packageName, String className) {
        return true; // TODO
    }

    private Element classElement(String containerName, String name) {
        return elements.getTypeElement(containerName + "." + name);
    }

    private CompletionItem completeFullyQualifiedClassName(String packageName, String className, String parentPackage) {
        assert packageName.startsWith(parentPackage);

        CompletionItem item = new CompletionItem();

        item.setKind(CompletionItemKind.Class);
        item.setLabel(packageName + "." + className);
        item.setInsertText(importInsertText(packageName, className, parentPackage));

        return item;
    }

    private String importInsertText(String packageName, String className, String parentPackage) {
        StringJoiner insertText = new StringJoiner(".");
        String[] packages = packageName.substring(parentPackage.length()).split("\\.");

        for (String each : packages) {
            if (!each.isEmpty())
                insertText.add(each);
        }

        insertText.add(className);

        return insertText.toString();
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

    private Stream<CompletionItem> constructors(Scope scope) {
        // TODO autocomplete classes that are imported *anywhere* on the source path and sort them second
        Collection<TypeElement> staticScopes = classScopes(scope);
        Stream<? extends Element> elements = Stream.empty();

        elements = Stream.concat(elements, staticScopes.stream().flatMap(this::staticMembers));
        elements = Stream.concat(elements, packageMembers(scope.getEnclosingClass()));

        return elements
                .filter(this::isTypeSymbol)
                .filter(e -> isAccessible(e, scope))
                .flatMap(this::explodeConstructors)
                .flatMap(this::completionItem);
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
        Collection<TypeElement> thisScopes = thisScopes(scope);
        Collection<TypeElement> classScopes = classScopes(scope);
        List<Scope> methodScopes = methodScopes(scope);
        Stream<? extends Element> elements = Stream.empty();

        if (!isStaticMethod(scope))
            elements = Stream.concat(elements, thisAndSuper(scope));

        elements = Stream.concat(elements, methodScopes.stream().flatMap(this::locals));
        elements = Stream.concat(elements, thisScopes.stream().flatMap(this::instanceMembers));
        elements = Stream.concat(elements, classScopes.stream().flatMap(this::staticMembers));
        elements = Stream.concat(elements, packageMembers(scope.getEnclosingClass()));
        elements = Stream.concat(elements, defaultImports());

        return elements
                .filter(e -> isAccessible(e, scope))
                .flatMap(this::completionItem);
    }

    private Collection<TypeElement> thisScopes(Scope scope) {
        Map<Name, TypeElement> acc = new LinkedHashMap<>();

        while (scope != null && scope.getEnclosingClass() != null) {
            TypeElement each = scope.getEnclosingClass();
            boolean staticMethod = isStaticMethod(scope);
            boolean staticClass = each.getModifiers().contains(Modifier.STATIC);
            boolean anonymousClass = isAnonymousClass(each);

            // If this scope is a static method, terminate
            if (staticMethod)
                break;
            // If the user has indicated this is a static class, it's the last scope in the chain
            else if (staticClass && !anonymousClass) {
                acc.put(each.getQualifiedName(), each);

                break;
            }
            // If this is an inner class, add it to the chain and keep going
            else {
                acc.put(each.getQualifiedName(), each);

                scope = scope.getEnclosingScope();
            }
        }

        return acc.values();
    }

    private List<Scope> methodScopes(Scope scope) {
        List<Scope> acc = new ArrayList<>();

        while (scope != null && scope.getEnclosingClass() != null) {
            if (scope.getEnclosingMethod() != null)
                acc.add(scope);

            scope = scope.getEnclosingScope();
        }

        return acc;
    }

    private Collection<TypeElement> classScopes(Scope scope) {
        Map<Name, TypeElement> acc = new LinkedHashMap<>();

        while (scope != null && scope.getEnclosingClass() != null) {
            TypeElement each = scope.getEnclosingClass();

            acc.putIfAbsent(each.getQualifiedName(), each);

            scope = scope.getEnclosingScope();
        }

        return acc.values();
    }

    private Stream<? extends Element> instanceMembers(TypeElement enclosingClass) {
        return elements.getAllMembers(enclosingClass).stream()
                .filter(each -> !each.getModifiers().contains(Modifier.STATIC));
    }

    private Stream<? extends Element> staticMembers(TypeElement enclosingClass) {
        return elements.getAllMembers(enclosingClass).stream()
                .filter(each -> each.getModifiers().contains(Modifier.STATIC));
    }

    private Stream<? extends Element> locals(Scope scope) {
        return StreamSupport.stream(scope.getLocalElements().spliterator(), false)
                .filter(e -> !isThisOrSuper(e));
    }

    private Stream<? extends Element> thisAndSuper(Scope scope) {
        return StreamSupport.stream(scope.getLocalElements().spliterator(), false)
                .filter(e -> isThisOrSuper(e));
    }

    private Stream<? extends Element> packageMembers(TypeElement enclosingClass) {
        return packageOf(enclosingClass)
                .map(PackageElement::getEnclosedElements)
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    private Stream<? extends Element> defaultImports() {
        return elements.getPackageElement("java.lang").getEnclosedElements().stream();
    }

    private Optional<PackageElement> packageOf(Element enclosing) {
        return Optional.ofNullable(elements.getPackageOf(enclosing));
    }

    private boolean isStaticMethod(Scope scope) {
        return scope.getEnclosingMethod() != null && scope.getEnclosingMethod().getModifiers().contains(Modifier.STATIC);
    }

    private boolean isAnonymousClass(Element candidate) {
        return candidate != null && candidate instanceof TypeElement && ((TypeElement) candidate).getNestingKind() == NestingKind.ANONYMOUS;
    }

    private boolean isThisOrSuper(Element each) {
        Name name = each.getSimpleName();

        return name.equals(thisName) || name.equals(superName);
    }

    private Stream<CompletionItem> completionItem(Element e) {
        String name = e.getKind() == ElementKind.CONSTRUCTOR ? Hovers.constructorName((ExecutableElement) e) : e.getSimpleName().toString();

        switch (e.getKind()) {
            case PACKAGE:
                return Stream.empty();
            case ENUM:
            case CLASS: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Class);
                item.setLabel(name);
                item.setInsertText(name);

                return Stream.of(item);
            }
            case ANNOTATION_TYPE:
            case INTERFACE:
            case TYPE_PARAMETER: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Reference);
                item.setLabel(name);

                return Stream.of(item);
            }
            case ENUM_CONSTANT: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Enum);
                item.setLabel(name);
                item.setDetail(e.getEnclosingElement().getSimpleName().toString());

                return Stream.of(item);
            }
            case FIELD: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Property);
                item.setLabel(name);
                item.setDetail(ShortTypePrinter.print(e.asType()));
                docstring(e).ifPresent(item::setDocumentation);

                return Stream.of(item);
            }
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER: {
                CompletionItem item = new CompletionItem();

                item.setKind(CompletionItemKind.Variable);
                item.setLabel(name);

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
                item.setSortText(name);
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
                item.setSortText(name);
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
        if (e == null)
            return false;

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
