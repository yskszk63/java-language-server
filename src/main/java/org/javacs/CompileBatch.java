package org.javacs;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.*;
import javax.tools.*;
import org.javacs.hover.ShortTypePrinter;
import org.javacs.lsp.*;

class CompileBatch implements AutoCloseable {
    static final int MAX_COMPLETION_ITEMS = 50;

    final JavaCompilerService parent;
    final ReusableCompiler.Borrow borrow;
    /** Indicates the task that requested the compilation is finished with it. */
    boolean closed;

    final JavacTask task;
    final Trees trees;
    final Elements elements;
    final Types types;
    final List<CompilationUnitTree> roots;

    CompileBatch(JavaCompilerService parent, Collection<? extends JavaFileObject> files) {
        this.parent = parent;
        this.borrow = batchTask(parent, files);
        this.task = borrow.task;
        this.trees = Trees.instance(borrow.task);
        this.elements = borrow.task.getElements();
        this.types = borrow.task.getTypes();
        this.roots = new ArrayList<CompilationUnitTree>();
        // Compile all roots
        try {
            for (var t : borrow.task.parse()) {
                roots.add(t);
            }
            // The results of borrow.task.analyze() are unreliable when errors are present
            // You can get at `Element` values using `Trees`
            borrow.task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * If the compilation failed because javac didn't find some package-private files in source files with different
     * names, list those source files.
     */
    Set<Path> needsAdditionalSources() {
        // Check for "class not found errors" that refer to package private classes
        var addFiles = new HashSet<Path>();
        for (var err : parent.diags) {
            if (!err.getCode().equals("compiler.err.cant.resolve.location")) continue;
            if (!isValidFileRange(err)) continue;
            var className = errorText(err);
            var packageName = packageName(err);
            var location = findPackagePrivateClass(packageName, className);
            if (location != FILE_NOT_FOUND) {
                addFiles.add(location);
            }
        }
        return addFiles;
    }

    private String errorText(javax.tools.Diagnostic<? extends javax.tools.JavaFileObject> err) {
        var file = Paths.get(err.getSource().toUri());
        var contents = FileStore.contents(file);
        var begin = (int) err.getStartPosition();
        var end = (int) err.getEndPosition();
        return contents.substring(begin, end);
    }

    private String packageName(javax.tools.Diagnostic<? extends javax.tools.JavaFileObject> err) {
        var file = Paths.get(err.getSource().toUri());
        return FileStore.packageName(file);
    }

    private static final Path FILE_NOT_FOUND = Paths.get("");

    private Path findPackagePrivateClass(String packageName, String className) {
        for (var file : FileStore.list(packageName)) {
            var parse = Parser.parseFile(file);
            for (var candidate : parse.packagePrivateClasses()) {
                if (candidate.contentEquals(className)) {
                    return file;
                }
            }
        }
        return FILE_NOT_FOUND;
    }

    @Override
    public void close() {
        closed = true;
    }

    private static ReusableCompiler.Borrow batchTask(
            JavaCompilerService parent, Collection<? extends JavaFileObject> sources) {
        parent.diags.clear();
        var options = options(parent.classPath, parent.addExports);
        return parent.compiler.getTask(null, parent.fileManager, parent.diags::add, options, List.of(), sources);
    }

    /** Combine source path or class path entries using the system separator, for example ':' in unix */
    private static String joinPath(Collection<Path> classOrSourcePath) {
        return classOrSourcePath.stream().map(p -> p.toString()).collect(Collectors.joining(File.pathSeparator));
    }

    private static List<String> options(Set<Path> classPath, Set<String> addExports) {
        var list = new ArrayList<String>();

        Collections.addAll(list, "-classpath", joinPath(classPath));
        Collections.addAll(list, "--add-modules", "ALL-MODULE-PATH");
        // Collections.addAll(list, "-verbose");
        Collections.addAll(list, "-proc:none");
        Collections.addAll(list, "-g");
        // You would think we could do -Xlint:all,
        // but some lints trigger fatal errors in the presence of parse errors
        Collections.addAll(
                list,
                "-Xlint:cast",
                "-Xlint:deprecation",
                "-Xlint:empty",
                "-Xlint:fallthrough",
                "-Xlint:finally",
                "-Xlint:path",
                "-Xlint:unchecked",
                "-Xlint:varargs",
                "-Xlint:static");
        for (var export : addExports) {
            list.add("--add-exports");
            list.add(export + "=ALL-UNNAMED");
        }

        return list;
    }

    CompilationUnitTree root(Path file) {
        for (var root : roots) {
            if (root.getSourceFile().toUri().equals(file.toUri())) {
                return root;
            }
        }
        // Somehow, file was not in batch
        var names = new StringJoiner(", ");
        for (var r : roots) {
            names.add(StringSearch.fileName(r.getSourceFile().toUri()));
        }
        throw new RuntimeException("File " + file + " isn't in batch " + names);
    }

    TreePath tree(Path file, int line, int character) {
        var root = root(file);
        var cursor = root.getLineMap().getPosition(line, character);
        return findPath(file, cursor);
    }

    Optional<Element> element(TreePath tree) {
        var el = trees.getElement(tree);
        return Optional.ofNullable(el);
    }

    private boolean isValidFileRange(javax.tools.Diagnostic<? extends JavaFileObject> d) {
        return d.getSource().toUri().getScheme().equals("file") && d.getStartPosition() >= 0 && d.getEndPosition() >= 0;
    }

    Range range(TreePath path) {
        try {
            var contents = path.getCompilationUnit().getSourceFile().getCharContent(true);
            return Parser.range(borrow.task, contents, path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    SourcePositions sourcePositions() {
        return trees.getSourcePositions();
    }

    /** Find all overloads for the smallest method call that includes the cursor */
    Optional<SignatureHelp> signatureHelp(Path file, int cursor) {
        var path = findPath(file, cursor);
        var invokePath = surroundingInvocation(path);
        if (invokePath == null) {
            return Optional.empty();
        }
        if (invokePath.getLeaf() instanceof MethodInvocationTree) {
            var invokeLeaf = (MethodInvocationTree) invokePath.getLeaf();
            var overloads = methodOverloads(invokePath);
            // Figure out which parameter is active
            var activeParameter = invokeLeaf.getArguments().indexOf(path.getLeaf());
            if (activeParameter == -1) activeParameter = 0;
            LOG.info(String.format("...active parameter `%s` is %d", path.getLeaf(), activeParameter));
            // Figure out which method is active, if possible
            var methodSelectPath = trees.getPath(invokePath.getCompilationUnit(), invokeLeaf.getMethodSelect());
            var methodEl = trees.getElement(methodSelectPath);
            ExecutableElement activeMethod = null;
            if (methodEl instanceof ExecutableElement) {
                activeMethod = (ExecutableElement) methodEl;
            }
            return Optional.of(asSignatureHelp(activeMethod, activeParameter, overloads));
        }
        if (invokePath.getLeaf() instanceof NewClassTree) {
            var invokeLeaf = (NewClassTree) invokePath.getLeaf();
            var overloads = constructorOverloads(invokePath);
            // Figure out which parameter is active
            var activeParameter = invokeLeaf.getArguments().indexOf(path.getLeaf());
            if (activeParameter == -1) activeParameter = 0;
            LOG.info(String.format("...active parameter `%s` is %d", path.getLeaf(), activeParameter));
            // Figure out which method is active, if possible
            var methodEl = trees.getElement(invokePath);
            ExecutableElement activeMethod = null;
            if (methodEl instanceof ExecutableElement) {
                activeMethod = (ExecutableElement) methodEl;
            }
            return Optional.of(asSignatureHelp(activeMethod, activeParameter, overloads));
        }
        return Optional.empty();
    }

    private TreePath surroundingInvocation(TreePath cursor) {
        for (var path = cursor; path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof MethodInvocationTree || path.getLeaf() instanceof NewClassTree) {
                return path;
            }
        }
        return null;
    }

    private List<ExecutableElement> methodOverloads(TreePath path) {
        // Find all overloads of method
        LOG.info(String.format("...`%s` is a method invocation", path.getLeaf()));
        var invoke = (MethodInvocationTree) path.getLeaf();
        var method = invoke.getMethodSelect();
        var scope = trees.getScope(path);
        if (method instanceof IdentifierTree) {
            var id = (IdentifierTree) method;
            return scopeOverloads(path.getCompilationUnit(), scope, id.getName());
        } else if (method instanceof MemberSelectTree) {
            var select = (MemberSelectTree) method;
            var containerPath = trees.getPath(path.getCompilationUnit(), select.getExpression());
            var containerEl = trees.getElement(containerPath);
            if (containerEl instanceof TypeElement) {
                return typeMemberOverloads(scope, (TypeElement) containerEl, select.getIdentifier());
            } else {
                var type = trees.getTypeMirror(containerPath);
                return instanceMemberOverloads(scope, type, select.getIdentifier());
            }
        } else {
            return List.of();
        }
    }

    private List<ExecutableElement> constructorOverloads(TreePath path) {
        // Find all overloads of method
        LOG.info(String.format("...`%s` is a constructor invocation", path.getLeaf()));
        var method = trees.getElement(path);
        var results = new ArrayList<ExecutableElement>();
        for (var m : method.getEnclosingElement().getEnclosedElements()) {
            if (m.getKind() == ElementKind.CONSTRUCTOR) {
                results.add((ExecutableElement) m);
            }
        }
        return results;
    }

    private List<ExecutableElement> scopeOverloads(CompilationUnitTree root, Scope scope, Name name) {
        var ids = identifiers(root, scope, candidate -> candidate.equals(name));
        var methods = new ArrayList<ExecutableElement>();
        for (var method : ids) {
            if (method instanceof ExecutableElement) {
                methods.add((ExecutableElement) method);
            }
        }
        return methods;
    }

    private List<ExecutableElement> typeMemberOverloads(Scope scope, TypeElement container, Name name) {
        var members = typeMembers(scope, container);
        var methods = new ArrayList<ExecutableElement>();
        for (var member : members) {
            if (member instanceof ExecutableElement && member.getSimpleName().equals(name)) {
                methods.add((ExecutableElement) member);
            }
        }
        return methods;
    }

    private List<ExecutableElement> instanceMemberOverloads(Scope scope, TypeMirror container, Name name) {
        var members = instanceMembers(scope, container);
        var methods = new ArrayList<ExecutableElement>();
        for (var member : members) {
            if (member instanceof ExecutableElement && member.getSimpleName().equals(name)) {
                methods.add((ExecutableElement) member);
            }
        }
        return methods;
    }

    private SignatureHelp asSignatureHelp(
            ExecutableElement activeMethod, int activeParameter, List<ExecutableElement> overloads) {
        var sigs = new ArrayList<SignatureInformation>();
        for (var e : overloads) {
            sigs.add(asSignatureInformation(e));
        }
        int activeSig = 0;
        if (activeMethod != null) {
            activeSig = overloads.indexOf(activeMethod);
        }
        return new SignatureHelp(sigs, activeSig, activeParameter);
    }

    private SignatureInformation asSignatureInformation(ExecutableElement e) {
        var i = new SignatureInformation();
        // Get docs from source if possible, ExecutableElement if not
        if (!addSignatureDocs(e, i)) {
            i.parameters = signatureParamsFromMethod(e);
        }
        // Compute label from params (which came from either source or ExecutableElement)
        var name = e.getSimpleName();
        if (name.contentEquals("<init>")) name = e.getEnclosingElement().getSimpleName();
        var args = new StringJoiner(", ");
        for (var p : i.parameters) {
            args.add(p.label);
        }
        i.label = name + "(" + args + ")";

        return i;
    }

    private boolean addSignatureDocs(ExecutableElement e, SignatureInformation sig) {
        // Find the file that contains e
        var ptr = new Ptr(e);
        var file = parent.docs.find(ptr);
        if (!file.isPresent()) return false;
        var parse = Parser.parseJavaFileObject(file.get());
        // Find the tree
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return false;
        if (!(path.get().getLeaf() instanceof MethodTree)) return false;
        var method = (MethodTree) path.get().getLeaf();
        // Find the docstring on method, or empty doc if there is none
        var doc = parse.doc(path.get());
        sig.documentation = MarkdownHelper.asMarkupContent(doc);
        // Get param docs from @param tags
        var paramComments = new HashMap<String, String>();
        for (var tag : doc.getBlockTags()) {
            if (tag.getKind() == DocTree.Kind.PARAM) {
                var param = (ParamTree) tag;
                paramComments.put(param.getName().toString(), MarkdownHelper.asMarkdown(param.getDescription()));
            }
        }
        // Get param names from source
        sig.parameters = new ArrayList<ParameterInformation>();
        for (var i = 0; i < e.getParameters().size(); i++) {
            var fromSource = method.getParameters().get(i);
            var fromType = e.getParameters().get(i);
            var info = new ParameterInformation();
            var name = fromSource.getName().toString();
            var type = ShortTypePrinter.DEFAULT.print(fromType.asType());
            info.label = type + " " + name;
            if (paramComments.containsKey(name)) {
                var markdown = paramComments.get(name);
                info.documentation = new MarkupContent("markdown", markdown);
            }
            sig.parameters.add(info);
        }
        return true;
    }

    private List<ParameterInformation> signatureParamsFromMethod(ExecutableElement e) {
        var missingParamNames = ShortTypePrinter.missingParamNames(e);
        var ps = new ArrayList<ParameterInformation>();
        for (var v : e.getParameters()) {
            var p = new ParameterInformation();
            if (missingParamNames) p.label = ShortTypePrinter.DEFAULT.print(v.asType());
            else p.label = v.getSimpleName().toString();
            ps.add(p);
        }
        return ps;
    }

    private List<Element> typeMembers(Scope scope, TypeElement element) {
        LOG.info(String.format("...completing static members of %s", element.getQualifiedName()));
        var result = new ArrayList<Element>();
        for (var member : element.getEnclosedElements()) {
            if (member.getModifiers().contains(Modifier.STATIC)
                    && trees.isAccessible(scope, member, (DeclaredType) element.asType())) {
                result.add(member);
            }
        }
        return result;
    }

    private List<Element> instanceMembers(Scope scope, TypeMirror type) {
        if (!hasMembers(type)) {
            LOG.warning("...don't know how to complete members of type " + type);
            return List.of();
        }
        var result = new ArrayList<Element>();
        // TODO consider replacing this with elements.getAllMembers(type)
        var ts = supersWithSelf(type);
        var alreadyAdded = new HashSet<String>();
        LOG.info(String.format("...completing virtual members of %s and %d supers", type, ts.size()));
        for (var t : ts) {
            var e = types.asElement(t);
            if (e == null) {
                LOG.warning(String.format("...can't convert supertype `%s` to element, skipping", t));
                continue;
            }
            for (var member : e.getEnclosedElements()) {
                // Don't add statics
                if (member.getModifiers().contains(Modifier.STATIC)) continue;
                // Don't add constructors
                if (member.getSimpleName().contentEquals("<init>")) continue;
                // Skip overridden members from superclass
                if (alreadyAdded.contains(member.toString())) continue;

                // If type is a DeclaredType, check accessibility of member
                if (type instanceof DeclaredType) {
                    if (trees.isAccessible(scope, member, (DeclaredType) type)) {
                        result.add(member);
                        alreadyAdded.add(member.toString());
                    }
                }
                // Otherwise, accessibility rules are very complicated
                // Give up and just declare that everything is accessible
                else {
                    result.add(member);
                    alreadyAdded.add(member.toString());
                }
            }
        }
        return result;
    }

    private Set<TypeMirror> supersWithSelf(TypeMirror t) {
        var types = new HashSet<TypeMirror>();
        collectSupers(t, types);
        // Object type is not included by default
        // We need to add it to get members like .equals(other) and .hashCode()
        types.add(borrow.task.getElements().getTypeElement("java.lang.Object").asType());
        return types;
    }

    private void collectSupers(TypeMirror t, Set<TypeMirror> supers) {
        supers.add(t);
        for (var s : types.directSupertypes(t)) {
            collectSupers(s, supers);
        }
    }

    private boolean hasMembers(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:
            case DECLARED:
            case ERROR:
            case TYPEVAR:
            case WILDCARD:
            case UNION:
            case INTERSECTION:
                return true;
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
            case FLOAT:
            case DOUBLE:
            case VOID:
            case NONE:
            case NULL:
            case PACKAGE:
            case EXECUTABLE:
            case OTHER:
            default:
                return false;
        }
    }

    private List<Element> identifiers(CompilationUnitTree root, Scope scope, Predicate<CharSequence> test) {
        var locals = scopeMembers(scope, test);
        LOG.info(String.format("...found %d locals", locals.size()));
        var statics = staticImports(root, test);
        LOG.info(String.format("...found %d static imports", statics.size()));
        var both = new ArrayList<Element>();
        both.addAll(statics);
        both.addAll(locals);
        return both;
    }

    /** Find all identifiers in scope at line:character */
    private List<Element> scopeMembers(Scope start, Predicate<CharSequence> test) {
        var isStatic = false;
        var results = new ArrayList<Element>();
        for (var s : fastScopes(start)) {
            if (s.getEnclosingMethod() != null) {
                isStatic = isStatic || s.getEnclosingMethod().getModifiers().contains(Modifier.STATIC);
            }
            for (var e : s.getLocalElements()) {
                var name = e.getSimpleName();
                if (!test.test(name)) continue;
                if (isStatic && name.contentEquals("this")) continue;
                if (isStatic && name.contentEquals("super")) continue;
                results.add(e);
            }
            if (s.getEnclosingClass() != null) {
                var c = s.getEnclosingClass();
                var t = (DeclaredType) c.asType();
                if (!trees.isAccessible(start, c)) continue;
                var members = elements.getAllMembers(c);
                for (var e : members) {
                    if (!test.test(e.getSimpleName())) continue;
                    if (!trees.isAccessible(start, e, t)) continue;
                    if (isStatic && !e.getModifiers().contains(Modifier.STATIC)) continue;
                    results.add(e);
                }
                isStatic = isStatic || c.getModifiers().contains(Modifier.STATIC);
            }
            // Return early?
            if (tooManyItems(results)) {
                return results;
            }
        }
        return results;
    }

    private List<Scope> fastScopes(Scope start) {
        var scopes = new ArrayList<Scope>();
        for (var s = start; s != null; s = s.getEnclosingScope()) {
            scopes.add(s);
        }
        // Scopes may be contained in an enclosing scope.
        // The outermost scope contains those elements available via "star import" declarations;
        // the scope within that contains the top level elements of the compilation unit, including any named
        // imports.
        // https://parent.docs.oracle.com/en/java/javase/11/docs/api/jdk.compiler/com/sun/source/tree/Scope.html
        return scopes.subList(0, scopes.size() - 2);
    }

    private boolean tooManyItems(List<Element> elements) {
        var distinctNames = new HashSet<Name>();
        for (var e : elements) {
            distinctNames.add(e.getSimpleName());
        }
        if (distinctNames.size() >= MAX_COMPLETION_ITEMS) {
            LOG.warning(String.format("...# of items %d reached max %s", distinctNames.size(), MAX_COMPLETION_ITEMS));
            return true;
        }
        return false;
    }

    private List<Element> staticImports(CompilationUnitTree root, Predicate<CharSequence> test) {
        var result = new ArrayList<Element>();
        for (var i : root.getImports()) {
            if (!i.isStatic()) continue;
            var id = (MemberSelectTree) i.getQualifiedIdentifier();
            var path = trees.getPath(root, id.getExpression());
            var el = (TypeElement) trees.getElement(path);
            if (id.getIdentifier().contentEquals("*")) {
                for (var member : el.getEnclosedElements()) {
                    if (test.test(member.getSimpleName()) && member.getModifiers().contains(Modifier.STATIC)) {
                        result.add(member);
                        if (tooManyItems(result)) return result;
                    }
                }
            } else {
                for (var member : el.getEnclosedElements()) {
                    if (test.test(member.getSimpleName()) && member.getModifiers().contains(Modifier.STATIC)) {
                        result.add(member);
                        if (tooManyItems(result)) return result;
                    }
                }
            }
        }
        return result;
    }

    /** Find the smallest tree that includes the cursor */
    TreePath findPath(Path file, long cursor) {
        var root = root(file);
        var finder = new FindSmallest(cursor, borrow.task, root);
        finder.scan(root, null);
        if (finder.found == null) {
            return new TreePath(root);
        }
        return finder.found;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
