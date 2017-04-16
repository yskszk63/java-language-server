package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class Completions {

    static Stream<CompletionItem> at(FocusedResult compiled) {
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

    private Stream<CompletionItem> get() {
        Tree leaf = path.getLeaf();
        Scope scope = trees.getScope(path);
        CursorContext context = CursorContext.from(path);

        if (leaf instanceof MemberSelectTree) {
            MemberSelectTree select = (MemberSelectTree) leaf;
            TreePath expressionPath = new TreePath(path.getParentPath(), select.getExpression());

            return completeMembers(expressionPath, partialIdentifier(select.getIdentifier()), scope, context);
        }
        else if (leaf instanceof MemberReferenceTree) {
            MemberReferenceTree select = (MemberReferenceTree) leaf;
            TreePath expressionPath = new TreePath(path.getParentPath(), select.getQualifierExpression());

            return completeMembers(expressionPath, partialIdentifier(select.getName()), scope, context);
        }
        else if (leaf instanceof IdentifierTree) {
            IdentifierTree id = (IdentifierTree) leaf;

            return completeIdentifier(partialIdentifier(id.getName()), scope, context);
        }
        else return Stream.empty();
    }

    private String partialIdentifier(Name name) {
        if (name.contentEquals("<error>"))
            return "";
        else
            return name.toString();
    }

    /**
     * Suggest all available identifiers
     */
    private Stream<CompletionItem> completeIdentifier(String partialIdentifier, Scope from, CursorContext context) {
        switch (context) {
            case Import:
                return packageMembers("", partialIdentifier, from)
                        .flatMap(this::completionItem);
            case NewClass:
                return allSymbols(partialIdentifier, from)
                        .flatMap(this::explodeConstructors)
                        .filter(init -> trees.isAccessible(from, init, (DeclaredType) init.getEnclosingElement().asType()))
                        .flatMap(this::completionItem);
            case Other:
            default: {
                Predicate<Element> accessible = e -> {
                    if (e == null)
                        return false;
                        // Class names
                    else if (e instanceof TypeElement)
                        return trees.isAccessible(from, (TypeElement) e);
                    else if (e.getEnclosingElement() == null)
                        return false;
                        // Members of other classes
                    else if (e.getEnclosingElement() instanceof DeclaredType)
                        return trees.isAccessible(from, e, (DeclaredType) e.getEnclosingElement());
                        // Local variables
                    else
                        return true;
                };

                return allSymbols(partialIdentifier, from)
                        .filter(accessible)
                        .flatMap(this::completionItem);
            }
        }
    }

    /**
     * Suggest all accessible members of expression
     */
    private Stream<CompletionItem> completeMembers(TreePath expression, String partialIdentifier, Scope from, CursorContext context) {
        switch (context) {
            case NewClass:
                return allMembers(expression, partialIdentifier, from)
                        .flatMap(this::explodeConstructors)
                        .filter(init -> trees.isAccessible(from, init, (DeclaredType) init.getEnclosingElement().asType()))
                        .flatMap(this::completionItem);
            case Import: {
                return allMembers(expression, partialIdentifier, from)
                        .filter(member -> !(member instanceof TypeElement) || trees.isAccessible(from, (TypeElement) member))
                        .flatMap(this::completionItem);
            }
            case Other:
            default: {
                // TODO this is not always a DeclaredType
                DeclaredType type = (DeclaredType) trees.getTypeMirror(expression);

                return allMembers(expression, partialIdentifier, from)
                        .filter(e -> trees.isAccessible(from, e, type))
                        .flatMap(this::completionItem);
            }
        }
    }

    private Stream<? extends Element> allMembers(TreePath expression, String partialIdentifier, Scope from) {
        Element element = trees.getElement(expression);

        if (element == null)
            return Stream.empty();

        // com.foo.?
        if (element instanceof PackageElement) {
            PackageElement packageElement = (PackageElement) element;

            return packageMembers(packageElement.getQualifiedName().toString(), partialIdentifier, from);
        }
        // MyClass.?
        else if (element instanceof TypeElement) {
            // OuterClass.this, OuterClass.super
            Stream<? extends Element> thisAndSuper = thisScopes(from).stream()
                    .filter(scope -> scope.getEnclosingClass().equals(element))
                    .flatMap(this::thisAndSuper);
            // MyClass.?
            Stream<? extends Element> members = elements.getAllMembers((TypeElement) element).stream()
                    .filter(e -> e.getModifiers().contains(Modifier.STATIC));
            // MyClass.class
            Element dotClass = new Symbol.VarSymbol(
                    Flags.PUBLIC | Flags.STATIC | Flags.FINAL,
                    (com.sun.tools.javac.util.Name) task.getElements().getName("class"),
                    (com.sun.tools.javac.code.Type) element.asType(),
                    (Symbol) element
            );

            return Stream.concat(Stream.of(dotClass), Stream.concat(thisAndSuper, members))
                    .filter(e -> containsCharactersInOrder(e.getSimpleName(), partialIdentifier));
        }
        // myValue.?
        else {
            DeclaredType type = (DeclaredType) trees.getTypeMirror(expression);
            List<? extends Element> members = elements.getAllMembers((TypeElement) type.asElement());

            return members.stream()
                    .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                    .filter(e -> containsCharactersInOrder(e.getSimpleName(), partialIdentifier));
        }
    }

    private Stream<? extends Element> packageMembers(String parentPackage, String partialIdentifier, Scope from) {
        // Source-path packages that match parentPackage.partialIdentifier
        Stream<PackageElement> packages = subPackages(parentPackage, partialIdentifier);
        Stream<TypeElement> sourcePathClasses = sourcePath.allSymbols(ElementKind.CLASS)
                .filter(c -> c.getContainerName().equals(parentPackage))
                .filter(c -> containsCharactersInOrder(c.getName(), partialIdentifier))
                .map(c -> elements.getTypeElement(qualifiedName(c.getContainerName(), c.getName())));
        Stream<TypeElement> classPathClasses = classPath.topLevelClassesIn(parentPackage, partialIdentifier, packageOf(from))
                .map(c -> elements.getTypeElement(c.getName()));

        return Stream.concat(packages, Stream.concat(sourcePathClasses, classPathClasses));
    }

    /**
     * All sub-packages of parentPackage that match partialIdentifier
     */
    private Stream<PackageElement> subPackages(String parentPackage, String partialIdentifier) {
        String prefix = parentPackage.isEmpty() ? "" : parentPackage + ".";
        Stream<String> sourcePathMembers = sourcePath.allSymbols(ElementKind.CLASS)
                .map(c -> c.getContainerName())
                .filter(p -> p.startsWith(prefix));
        Stream<String> classPathMembers = classPath.packagesStartingWith(prefix);
        Set<String> next = Stream.concat(sourcePathMembers, classPathMembers)
                .map(p -> p.substring(prefix.length()))
                .map(Completions::firstId)
                .filter(p -> containsCharactersInOrder(p, partialIdentifier))
                .collect(Collectors.toSet());

        // Load 1 member of package to force javac to recognize that it exists
        for (String part : next) {
            classPath.loadPackage(prefix + part)
                    .ifPresent(this::tryLoad);
        }

        return next.stream()
                .map(last -> elements.getPackageElement(prefix + last))
                .filter(sym -> sym != null);
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
            else if (importName.equals(qualifiedName))
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

    private Stream<ExecutableElement> explodeConstructors(Element element) {
        if (element.getKind() != ElementKind.CLASS)
            return Stream.empty();

        try {
            return elements.getAllMembers((TypeElement) element).stream()
                    .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                    .map(e -> (ExecutableElement) e);
        } catch (Symbol.CompletionFailure failed) {
            LOG.warning(failed.getMessage());

            return Stream.empty();
        }
    }

    /**
     * Suggest all completions that are visible from scope
     */
    private Stream<? extends Element> allSymbols(String partialIdentifier, Scope scope) {
        Stream<? extends Element> sourcePathItems = alreadyImportedSymbols(scope)
                .filter(e -> containsCharactersInOrder(e.getSimpleName(), partialIdentifier));
        Stream<TypeElement> sourcePathClasses = sourcePathClasses(partialIdentifier);
        Stream<TypeElement> classPathItems = classPath.topLevelClasses(partialIdentifier, packageOf(scope))
                .flatMap(this::tryLoad);

        return Stream.concat(sourcePathItems, Stream.concat(sourcePathClasses, classPathItems));
    }

    private Stream<TypeElement> tryLoad(Class<?> c) {
        try {
            TypeElement type = elements.getTypeElement(c.getName());

            if (type != null)
                return Stream.of(type);
            else
                return Stream.empty();
        } catch (Symbol.CompletionFailure failed) {
            LOG.warning(failed.getMessage());

            return Stream.empty();
        }
    }

    private Stream<? extends Element> alreadyImportedSymbols(Scope scope) {
        Collection<TypeElement> thisScopes = scopeClasses(thisScopes(scope));
        Collection<TypeElement> classScopes = classScopes(scope);
        List<Scope> methodScopes = methodScopes(scope);
        Stream<? extends Element> staticImports = compilationUnit.getImports().stream().flatMap(this::staticImports);
        Stream<? extends Element> elements = Stream.empty();

        if (!isStaticMethod(scope))
            elements = Stream.concat(elements, thisAndSuper(scope));

        elements = Stream.concat(elements, methodScopes.stream().flatMap(this::locals));
        elements = Stream.concat(elements, thisScopes.stream().flatMap(this::instanceMembers));
        elements = Stream.concat(elements, classScopes.stream().flatMap(this::staticMembers));
        elements = Stream.concat(elements, staticImports);

        return elements;
    }

    private Stream<TypeElement> sourcePathClasses(String partialClass) {
        return sourcePath.allSymbols(ElementKind.CLASS)
                .filter(symbol -> containsCharactersInOrder(symbol.getName(), partialClass))
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

    private Stream<? extends Element> staticImports(ImportTree tree) {
        if (!tree.isStatic())
            return Stream.empty();

        if (isStarImport(tree)) {
            String parentName = mostIds(importId(tree));
            TypeElement parentElement = elements.getTypeElement(parentName);

            if (parentElement == null) {
                LOG.warning("Can't find " + parentName);

                return Stream.empty();
            }

            return parentElement.getEnclosedElements().stream();
        }
        else {
            String name = importId(tree);
            String className = mostIds(name);
            String memberName = lastId(name);
            TypeElement classElement = elements.getTypeElement(className);

            if (classElement == null) {
                LOG.warning("Can't find " + className);

                return Stream.empty();
            }

            for (Element each : classElement.getEnclosedElements()) {
                if (each.getSimpleName().contentEquals(memberName))
                    return Stream.of(each);
            }

            LOG.warning("Couldn't find " + memberName + " in " + className);

            return Stream.empty();
        }
    }

    private List<Scope> thisScopes(Scope scope) {
        List<Scope> acc = new ArrayList<>();

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
                acc.add(scope);

                break;
            }
            // If this is an inner class, add it to the chain and keep going
            else {
                acc.add(scope);

                scope = scope.getEnclosingScope();
            }
        }

        return acc;
    }

    private Collection<TypeElement> scopeClasses(Collection<Scope> scopes) {
        Map<Name, TypeElement> acc = new LinkedHashMap<>();

        for (Scope scope : scopes) {
            TypeElement each = scope.getEnclosingClass();

            acc.put(each.getQualifiedName(), each);
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
        try {
            String name = e.getSimpleName().toString();

            switch (e.getKind()) {
                case PACKAGE: {
                    PackageElement p = (PackageElement) e;
                    CompletionItem item = new CompletionItem();
                    String id = lastId(p.getSimpleName().toString());

                    item.setKind(CompletionItemKind.Module);
                    item.setLabel(id);
                    item.setInsertText(id);
                    item.setSortText("0/" + id);

                    return Stream.of(item);
                }
                case ENUM:
                case INTERFACE:
                case ANNOTATION_TYPE:
                case CLASS: {
                    TypeElement type = (TypeElement) e;
                    int order = isAlreadyImported(type.getQualifiedName().toString()) ? 1 : 2;
                    CompletionItem item = new CompletionItem();

                    item.setKind(classKind(e.getKind()));
                    item.setLabel(name);
                    item.setDetail(type.getQualifiedName().toString());
                    item.setInsertText(name);

                    PackageElement classPackage = elements.getPackageOf(e);
                    if (classPackage != null)
                        item.setDetail(classPackage.getQualifiedName().toString());

                    item.setAdditionalTextEdits(addImport(((TypeElement) e).getQualifiedName().toString()));
                    item.setSortText(order + "/" + name);
                    
                    Javadocs.global().classDoc(type)
                        .ifPresent(doc -> item.setDocumentation(doc.commentText()));

                    return Stream.of(item);
                }
                case TYPE_PARAMETER: {
                    CompletionItem item = new CompletionItem();

                    item.setKind(CompletionItemKind.Reference);
                    item.setLabel(name);
                    item.setSortText("0/" + name);

                    return Stream.of(item);
                }
                case ENUM_CONSTANT: {
                    CompletionItem item = new CompletionItem();

                    item.setKind(CompletionItemKind.Enum);
                    item.setLabel(name);
                    item.setDetail(e.getEnclosingElement().getSimpleName().toString());
                    item.setSortText("0/" + name);

                    return Stream.of(item);
                }
                case FIELD: {
                    CompletionItem item = new CompletionItem();

                    item.setKind(CompletionItemKind.Property);
                    item.setLabel(name);
                    item.setDetail(ShortTypePrinter.print(e.asType()));
                    item.setSortText("0/" + name);

                    return Stream.of(item);
                }
                case PARAMETER:
                case LOCAL_VARIABLE:
                case EXCEPTION_PARAMETER: {
                    CompletionItem item = new CompletionItem();

                    item.setKind(CompletionItemKind.Variable);
                    item.setLabel(name);
                    item.setSortText("0/" + name);

                    return Stream.of(item);
                }
                case METHOD: {
                    ExecutableElement method = (ExecutableElement) e;
                    CompletionItem item = new CompletionItem();

                    item.setKind(CompletionItemKind.Method);
                    item.setLabel(name);
                    item.setDetail(Hovers.methodSignature(method, true, false));
                    item.setInsertText(name); // TODO
                    item.setSortText(name);
                    item.setFilterText(name);
                    item.setSortText("0/" + name);
                    Javadocs.global().methodDoc(method)
                        .flatMap(Javadocs::commentText)
                        .ifPresent(item::setDocumentation);

                    return Stream.of(item);
                }
                case CONSTRUCTOR: {
                    TypeElement enclosingClass = (TypeElement) e.getEnclosingElement();
                    int order = isAlreadyImported(enclosingClass.getQualifiedName().toString()) ? 1 : 2;
                    name = enclosingClass.getSimpleName().toString();

                    ExecutableElement method = (ExecutableElement) e;
                    CompletionItem item = new CompletionItem();
                    String insertText = name;

                    if (!enclosingClass.getTypeParameters().isEmpty())
                        insertText += "<>";

                    item.setKind(CompletionItemKind.Constructor);
                    item.setLabel(name);
                    item.setDetail(Hovers.methodSignature(method, false, false));
                    item.setInsertText(insertText);
                    item.setSortText(name);
                    item.setFilterText(name);
                    item.setAdditionalTextEdits(addImport(enclosingClass.getQualifiedName().toString()));
                    item.setSortText(order + "/" + name);
                    Javadocs.global().constructorDoc(method)
                        .ifPresent(doc -> item.setDocumentation(doc.commentText()));

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
        } catch (Symbol.CompletionFailure failed) {
            LOG.warning(failed.getMessage());

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
        if (!isAlreadyImported(qualifiedName) && CursorContext.from(path) != CursorContext.Import)
            return new RefactorFile(task, compilationUnit).addImport(mostIds(qualifiedName), lastId(qualifiedName));
        else
            return Collections.emptyList();
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
