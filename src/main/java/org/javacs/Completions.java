package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Completions implements Supplier<Stream<CompletionItem>> {

    public static Stream<CompletionItem> at(FocusedResult compiled) {
        return compiled.cursor
                .map(path -> new Completions(compiled.task, compiled.classPath, compiled.sourcePath, path).get())
                .orElseGet(Stream::empty);
    }

    private final JavacTask task;
    private final ClassPathIndex classPath;
    private final SymbolIndex sourcePath;
    private final Trees trees;
    private final Elements elements;
    private final Name thisName, superName;
    private final CompilationUnitTree compilationUnit;
    private final TreePath path;

    private Completions(JavacTask task, ClassPathIndex classPath, SymbolIndex sourcePath, TreePath path) {
        this.task = task;
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.thisName = task.getElements().getName("this");
        this.superName = task.getElements().getName("super");
        this.classPath = classPath;
        this.sourcePath = sourcePath;
        this.compilationUnit = path.getCompilationUnit();
        this.path = path;
    }

    @Override
    public Stream<CompletionItem> get() {
        Tree leaf = path.getLeaf();
        Scope scope = trees.getScope(path);

        if (leaf instanceof MemberSelectTree) {
            MemberSelectTree select = (MemberSelectTree) leaf;
            TreePath expressionPath = new TreePath(path.getParentPath(), select.getExpression());

            return completeMembers(expressionPath, partialIdentifier(select.getIdentifier()), scope);
        }
        else if (leaf instanceof MemberReferenceTree) {
            MemberReferenceTree select = (MemberReferenceTree) leaf;
            TreePath expressionPath = new TreePath(path.getParentPath(), select.getQualifierExpression());

            return completeMembers(expressionPath, partialIdentifier(select.getName()), scope);
        }
        else if (leaf instanceof NewClassTree) {
            NewClassTree newClass = (NewClassTree) leaf;
            ExpressionTree identifier = newClass.getIdentifier();

            if (identifier instanceof MemberSelectTree) {
                MemberSelectTree select = (MemberSelectTree) identifier;
                TreePath pathToExpression = TreePath.getPath(TreePath.getPath(path, identifier), select.getExpression());

                return innerConstructors(pathToExpression, partialIdentifier(select.getIdentifier()), scope);
            }
            else if (identifier instanceof IdentifierTree) {
                return constructors(partialIdentifier(((IdentifierTree) identifier).getName()), scope);
            }
            else {
                LOG.warning("Expected MemberSelectTree or IdentifierTree but found " + identifier.getClass() + ", cannot complete constructor");

                return Stream.empty();
            }
        }
        else if (leaf instanceof IdentifierTree) {
            IdentifierTree id = (IdentifierTree) leaf;

            // Special case: import com
            if (inImport(path))
                return packageMembers("", id.getName().toString(), scope);

            return allSymbols(partialIdentifier(id.getName()), scope);
        }
        else return Stream.empty();
    }

    private static boolean inImport(TreePath path) {
        if (path == null)
            return false;
        else if (path.getLeaf().getKind() == Tree.Kind.IMPORT)
            return true;
        else
            return inImport(path.getParentPath());
    }

    private String partialIdentifier(Name name) {
        if (name.contentEquals("<error>"))
            return "";
        else
            return name.toString();
    }

    /**
     * Suggest all accessible members of expression
     */
    private Stream<CompletionItem> completeMembers(TreePath expression, String partialIdentifier, Scope from) {
        Element element = trees.getElement(expression);

        if (element instanceof PackageElement) {
            PackageElement packageElement = (PackageElement) element;

            return packageMembers(packageElement.getQualifiedName().toString(), partialIdentifier, from);
        }

        TypeMirror type = trees.getTypeMirror(expression);

        if (element == null || type == null)
            return Stream.empty();

        boolean isStatic = isTypeSymbol(element);
        List<? extends Element> all = members(type);

        Stream<CompletionItem> filter = all.stream()
                .filter(e -> containsCharactersInOrder(e.getSimpleName(), partialIdentifier))
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

    private Stream<CompletionItem> packageMembers(String parentPackage, String partialIdentifier, Scope from) {
        // Source-path packages that match parentPackage.partialIdentifier
        Stream<CompletionItem> packageItems = subPackages(parentPackage, partialIdentifier).stream()
                .map(this::completePackagePart);
        Stream<TypeElement> sourcePathClasses = sourcePath.allSymbols(ElementKind.CLASS)
                .filter(c -> c.getContainerName().equals(parentPackage))
                .filter(c -> containsCharactersInOrder(c.getName(), partialIdentifier))
                .map(c -> elements.getTypeElement(qualifiedName(c.getContainerName(), c.getName())));
        Stream<TypeElement> classPathClasses = classPath.topLevelClassesIn(parentPackage, partialIdentifier, packageOf(from))
                .map(c -> elements.getTypeElement(c.getName()));
        Stream<CompletionItem> classItems = Stream.concat(sourcePathClasses, classPathClasses)
                .filter(e -> isAccessible(e, from))
                .flatMap(this::completionItem);

        return Stream.concat(packageItems, classItems);
    }

    /**
     * Complete a single identifier as part of a package chain.
     * This isn't really a Java concept, so we have to implement a special case rather than use {@link this#completionItem(Element)}
     */
    private CompletionItem completePackagePart(String id) {
        CompletionItem item = new CompletionItem();

        item.setKind(CompletionItemKind.Module);
        item.setLabel(id);

        return item;
    }

    /**
     * All sub-packages of parentPackage that match partialIdentifier
     */
    private Set<String> subPackages(String parentPackage, String partialIdentifier) {
        String prefix = parentPackage.isEmpty() ? "" : parentPackage + ".";
        Stream<String> sourcePathMembers = sourcePath.allSymbols(ElementKind.CLASS)
                .map(c -> c.getContainerName())
                .filter(p -> p.startsWith(prefix));
        Stream<String> classPathMembers = classPath.packagesStartingWith(prefix);

        return Stream.concat(sourcePathMembers, classPathMembers)
                .map(p -> p.substring(prefix.length()))
                .map(Completions::firstId)
                .filter(p -> containsCharactersInOrder(p, partialIdentifier))
                .collect(Collectors.toSet());
    }

    private static String qualifiedName(String parentPackage, String partialIdentifier) {
        if (parentPackage.isEmpty())
            return partialIdentifier;
        else if (partialIdentifier.isEmpty())
            return parentPackage;
        else
            return parentPackage + "." + partialIdentifier;
    }

    private String packageOf(Scope from) {
        TypeElement enclosingClass = from.getEnclosingClass();

        if (enclosingClass == null)
            return "";

        PackageElement enclosingPackage = elements.getPackageOf(enclosingClass);

        if (enclosingPackage == null)
            return "";

        return enclosingPackage.getQualifiedName().toString();
    }

    private Stream<TypeElement> topLevelClassElement(Element e) {
        if (e == null || e.getKind() != ElementKind.CLASS)
            return Stream.empty();

        TypeElement candidate = (TypeElement) e;
        Element parent = candidate.getEnclosingElement();

        if (parent == null || parent.getKind() == ElementKind.PACKAGE)
            return Stream.of(candidate);

        return Stream.empty();
    }

    private boolean isAlreadyImported(String qualifiedName) {
        String packageName = mostIds(qualifiedName);

        if (packageName.equals("java.lang"))
            return true;

        if (packageName.equals(compilationUnit.getPackageName().toString()))
            return true;

        for (ImportTree each : compilationUnit.getImports()) {
            if (each.isStatic())
                continue;

            String importName = importId(each);

            if (isStarImport(each) && mostIds(importName).equals(packageName))
                return true;
            else if (importName.equals(packageName))
                return true;
        }

        return false;
    }

    private static boolean isStarImport(ImportTree tree) {
        String importName = importId(tree);

        return importName.endsWith(".*");
    }

    private static String importId(ImportTree tree) {
        return tree.getQualifiedIdentifier().toString();
    }

    private static String firstId(String qualifiedName) {
        int firstDot = qualifiedName.indexOf('.');

        if (firstDot == -1)
            return qualifiedName;
        else
            return qualifiedName.substring(0, firstDot);
    }

    private static String mostIds(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');

        if (lastDot == -1)
            return qualifiedName;
        else
            return qualifiedName.substring(0, lastDot);
    }

    private static String lastId(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');

        if (lastDot == -1)
            return qualifiedName;
        else
            return qualifiedName.substring(lastDot + 1);
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

    private Stream<CompletionItem> constructors(String partialClass, Scope scope) {
        Stream<TypeElement> alreadyImported = sourcePathClasses().filter(e -> isAlreadyImported(e.getQualifiedName().toString()));
        Stream<TypeElement> notImported = sourcePathClasses().filter(e -> !isAlreadyImported(e.getQualifiedName().toString()));
        Stream<CompletionItem> sourcePathItems = Stream.concat(alreadyImported, notImported)
                .filter(e -> containsCharactersInOrder(e.getSimpleName(), partialClass))
                .flatMap(this::topLevelClassElement)
                .flatMap(this::explodeConstructors)
                .map(this::completeJavacConstructor);
        Stream<CompletionItem> classPathItems = classPath.topLevelConstructors(partialClass, packageOf(scope))
                .flatMap(this::asJavacConstructor)
                .flatMap(this::tryCompleteJavacConstructor);

        return Stream.concat(sourcePathItems, classPathItems);
    }

    private Stream<ExecutableElement> asJavacConstructor(Constructor<?> c) {
        TypeElement declaringClass = elements.getTypeElement(c.getDeclaringClass().getName());

        if (declaringClass == null)
            return Stream.empty();

        // Completing constructors from the classpath can fail
        try {
            return elements.getAllMembers(declaringClass).stream()
                .filter(member -> member.getKind() == ElementKind.CONSTRUCTOR)
                .map(member -> (ExecutableElement) member);
        } catch (Symbol.CompletionFailure failed) {
            LOG.warning(failed.getMessage());

            return Stream.empty();
        }
    }

    private Stream<CompletionItem> innerConstructors(TreePath parent, String partialClass, Scope scope) {
        Element element = trees.getElement(parent);

        if (element == null)
            return Stream.empty();

        return members(element.asType()).stream()
                .filter(el -> el.getKind() == ElementKind.CLASS)
                .map(el -> (TypeElement) el)
                .filter(el -> containsCharactersInOrder(el.getSimpleName(), partialClass))
                .flatMap(this::explodeConstructors)
                .filter(el -> isAccessible(el, scope))
                .map(this::completeJavacConstructor);
    }

    private Stream<CompletionItem> tryCompleteJavacConstructor(ExecutableElement method) {
        // Completing constructors from the classpath can fail
        try {
            return Stream.of(completeJavacConstructor(method));
        } catch (Symbol.CompletionFailure failed) {
            LOG.warning(failed.getMessage());

            return Stream.empty();
        }
    }

    private CompletionItem completeJavacConstructor(ExecutableElement method) {
        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        Optional<String> docString = docstring(method);
        boolean hasTypeParameters = !method.getTypeParameters().isEmpty();
        String methodSignature = Hovers.methodSignature(method, true);
        String qualifiedName = enclosingClass.getQualifiedName().toString();
        String name = enclosingClass.getSimpleName().toString();

        return completeConstructor(qualifiedName, name, hasTypeParameters, methodSignature, docString);
    }

    private CompletionItem completeConstructor(String qualifiedName, String name, boolean hasTypeParameters, String methodSignature, Optional<String> docString) {
        CompletionItem item = new CompletionItem();
        String insertText = name;

        if (hasTypeParameters)
            insertText += "<>";

        item.setKind(CompletionItemKind.Constructor);
        item.setLabel(methodSignature);
        docString.ifPresent(item::setDocumentation);
        item.setInsertText(insertText);
        item.setSortText(name);
        item.setFilterText(name);
        item.setAdditionalTextEdits(addImport(qualifiedName));

        return item;
    }

    private Stream<ExecutableElement> explodeConstructors(Element element) {
        switch (element.getKind()) {
            case CLASS:
                return members(element.asType()).stream()
                        .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                        .map(e -> (ExecutableElement) e);
            case CONSTRUCTOR:
                return Stream.of((ExecutableElement) element);
            default:
                return Stream.empty();
        }
    }

    /**
     * Suggest all completions that are visible from scope
     */
    private Stream<CompletionItem> allSymbols(String partialIdentifier, Scope scope) {
        Stream<CompletionItem> sourcePathItems = allSourcePathSymbols(scope)
                .filter(e -> containsCharactersInOrder(e.getSimpleName(), partialIdentifier))
                .filter(e -> isAccessible(e, scope))
                .flatMap(this::completionItem);
        Stream<CompletionItem> classPathItems = classPath.topLevelClasses(partialIdentifier, packageOf(scope))
                .map(this::completeTopLevelClassSymbol);

        return Stream.concat(sourcePathItems, classPathItems);
    }

    private CompletionItem completeTopLevelClassSymbol(Class<?> c) {
        CompletionItem item = new CompletionItem();

        item.setKind(CompletionItemKind.Class);
        item.setLabel(c.getSimpleName());
        item.setDetail(c.getPackage().getName());
        item.setInsertText(c.getSimpleName());
        item.setAdditionalTextEdits(addImport(c.getName()));

        return item;
    }

    private Stream<? extends Element> allSourcePathSymbols(Scope scope) {
        Collection<TypeElement> thisScopes = thisScopes(scope);
        Collection<TypeElement> classScopes = classScopes(scope);
        List<Scope> methodScopes = methodScopes(scope);
        Stream<TypeElement> alreadyImported = sourcePathClasses().filter(e -> isAlreadyImported(e.getQualifiedName().toString()));
        Stream<TypeElement> notImported = sourcePathClasses().filter(e -> !isAlreadyImported(e.getQualifiedName().toString()));
        Stream<? extends Element> elements = Stream.empty();

        if (!isStaticMethod(scope))
            elements = Stream.concat(elements, thisAndSuper(scope));

        elements = Stream.concat(elements, methodScopes.stream().flatMap(this::locals));
        elements = Stream.concat(elements, thisScopes.stream().flatMap(this::instanceMembers));
        elements = Stream.concat(elements, classScopes.stream().flatMap(this::staticMembers));
        elements = Stream.concat(elements, alreadyImported);
        elements = Stream.concat(elements, notImported);

        return elements;
    }

    private Stream<TypeElement> sourcePathClasses() {
        return sourcePath.allSymbols(ElementKind.CLASS)
                .flatMap(this::typeElementForSymbol)
                .flatMap(this::topLevelClassElement);
    }

    private Stream<TypeElement> typeElementForSymbol(SymbolInformation symbol) {
        TypeElement result = elements.getTypeElement(qualifiedName(symbol.getContainerName(), symbol.getName()));

        if (result != null)
            return Stream.of(result);
        else 
            return Stream.empty();
    }

    /**
     * All imported symbols
     */
    private Stream<? extends Element> importedSymbolsIn(ImportTree tree) {
        if (isStarImport(tree)) {
            String parentName = mostIds(importId(tree));
            PackageElement parentElement = elements.getPackageElement(parentName);

            return parentElement.getEnclosedElements().stream();
        }
        else {
            String name = importId(tree);
            TypeElement element = elements.getTypeElement(name);

            return Stream.of(element);
        }
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
        String name = e.getSimpleName().toString();

        switch (e.getKind()) {
            case PACKAGE: {
                PackageElement p = (PackageElement) e;
                CompletionItem item = new CompletionItem();
                String id = lastId(p.getSimpleName().toString());

                item.setKind(CompletionItemKind.Module);
                item.setLabel(id);
                item.setInsertText(id);

                return Stream.of(item);
            }
            case ENUM:
            case INTERFACE:
            case ANNOTATION_TYPE:
            case CLASS: {
                CompletionItem item = new CompletionItem();

                item.setKind(classKind(e.getKind()));
                item.setLabel(name);
                item.setInsertText(name);

                PackageElement classPackage = elements.getPackageOf(e);
                if (classPackage != null)
                    item.setDetail(classPackage.getSimpleName().toString());

                item.setAdditionalTextEdits(addImport(((TypeElement)e).getQualifiedName().toString()));

                return Stream.of(item);
            }
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
                item.setLabel(Hovers.methodSignature(method, false));
                item.setDetail(ShortTypePrinter.print(method.getReturnType()));
                docstring(e).ifPresent(item::setDocumentation);
                item.setInsertText(name); // TODO
                item.setSortText(name);
                item.setFilterText(name);

                return Stream.of(item);
            }
            case CONSTRUCTOR: {
                // Constructors are completed differently
                return Stream.empty();
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

    private CompletionItemKind classKind(ElementKind kind) {
        switch (kind) {
            case CLASS:
                return CompletionItemKind.Class;
            case ANNOTATION_TYPE:
            case INTERFACE:
                return CompletionItemKind.Interface;
            case ENUM:
                return CompletionItemKind.Enum;
            default:
                throw new RuntimeException("Expected CLASS, INTERFACE or ENUM but found " + kind);
        }
    }

    private List<TextEdit> addImport(String qualifiedName) {
        if (!isAlreadyImported(qualifiedName) && !inImport(path))
            return new RefactorFile(task, compilationUnit).addImport(mostIds(qualifiedName), lastId(qualifiedName));
        else
            return Collections.emptyList();
    }

    private boolean isAccessible(Element e, Scope scope) {
        if (e == null)
            return false;

        TypeMirror enclosing = e.getEnclosingElement().asType();

        if (enclosing instanceof DeclaredType)
            return trees.isAccessible(scope, e, (DeclaredType) enclosing);
        else if (e instanceof TypeElement)
            return trees.isAccessible(scope, (TypeElement) e);
        else
            return true;
    }

    private Optional<String> docstring(Element e) {
        return Optional.ofNullable(elements.getDocComment(e));
    }

    public static boolean containsCharactersInOrder(CharSequence candidate, CharSequence pattern) {
        int iCandidate = 0, iPattern = 0;

        while (iCandidate < candidate.length() && iPattern < pattern.length()) {
            char patternChar = Character.toLowerCase(pattern.charAt(iPattern));
            char testChar = Character.toLowerCase(candidate.charAt(iCandidate));

            if (patternChar == testChar) {
                iPattern++;
                iCandidate++;
            }
            else iCandidate++;
        }

        return iPattern == pattern.length();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
