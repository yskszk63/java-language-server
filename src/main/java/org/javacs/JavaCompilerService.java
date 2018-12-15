package org.javacs;

import com.google.common.collect.Sets;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

// TODO eliminate uses of URI in favor of Path
public class JavaCompilerService {
    private static final Logger LOG = Logger.getLogger("main");
    // Not modifiable! If you want to edit these, you need to create a new instance
    private final Set<Path> sourcePath, classPath, docPath;
    private final JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
    private final Docs docs;
    private final ClassSource jdkClasses = Classes.jdkTopLevelClasses(), classPathClasses;
    // Diagnostics from the last compilation task
    private final List<Diagnostic<? extends JavaFileObject>> diags = new ArrayList<>();
    // Use the same file manager for multiple tasks, so we don't repeatedly re-compile the same files
    private final StandardJavaFileManager fileManager =
            new FileManagerWrapper(compiler.getStandardFileManager(diags::add, null, Charset.defaultCharset()));
    // Cache a single compiled file
    // Since the user can only edit one file at a time, this should be sufficient
    private Cache cache;

    public JavaCompilerService(Set<Path> sourcePath, Set<Path> classPath, Set<Path> docPath) {
        System.err.println("Source path:");
        for (var p : sourcePath) {
            System.err.println("  " + p);
        }
        System.err.println("Class path:");
        for (var p : classPath) {
            System.err.println("  " + p);
        }
        System.err.println("Doc path:");
        for (var p : docPath) {
            System.err.println("  " + p);
        }
        // sourcePath and classPath can't actually be modified, because JavaCompiler remembers them from task to task
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.classPath = Collections.unmodifiableSet(classPath);
        this.docPath = Collections.unmodifiableSet(docPath);
        this.docs = new Docs(Sets.union(sourcePath, docPath));
        this.classPathClasses = Classes.classPathTopLevelClasses(classPath);
    }

    private Optional<Path> relativeToSourcePath(URI source) {
        var p = Paths.get(source);
        for (var root : sourcePath) {
            if (p.startsWith(root)) {
                var rel = root.relativize(p.getParent());
                return Optional.of(rel);
            }
        }
        return Optional.empty();
    }

    private Set<String> subPackages(String parentPackage) {
        var result = new HashSet<String>();
        Consumer<String> checkClassName =
                name -> {
                    var packageName = Parser.mostName(name);
                    if (packageName.startsWith(parentPackage) && packageName.length() > parentPackage.length()) {
                        var start = parentPackage.length() + 1;
                        var end = packageName.indexOf('.', start);
                        if (end == -1) end = packageName.length();
                        var prefix = packageName.substring(0, end);
                        result.add(prefix);
                    }
                };
        for (var name : jdkClasses.classes()) checkClassName.accept(name);
        for (var name : classPathClasses.classes()) checkClassName.accept(name);
        return result;
    }

    /** Combine source path or class path entries using the system separator, for example ':' in unix */
    private static String joinPath(Collection<Path> classOrSourcePath) {
        return classOrSourcePath.stream().map(p -> p.toString()).collect(Collectors.joining(File.pathSeparator));
    }

    private static List<String> options(Set<Path> sourcePath, Set<Path> classPath, boolean lint) {
        var list = new ArrayList<String>();

        Collections.addAll(list, "-classpath", joinPath(classPath));
        Collections.addAll(list, "-sourcepath", joinPath(sourcePath));
        // Collections.addAll(list, "-verbose");
        Collections.addAll(list, "-proc:none");
        Collections.addAll(list, "-g");
        // You would think we could do -Xlint:all,
        // but some lints trigger fatal errors in the presence of parse errors
        Collections.addAll(list, 
            "-Xlint:cast", 
            "-Xlint:deprecation", 
            "-Xlint:empty", 
            "-Xlint:fallthrough", 
            "-Xlint:finally", 
            "-Xlint:path", 
            "-Xlint:unchecked", 
            "-Xlint:varargs", 
            "-Xlint:static");
        if (lint) {
            // Add error-prone
            // TODO re-enable when error-prone is stable for Java 10 https://github.com/google/error-prone/issues/1124
            // Collections.addAll(list, "-XDcompilePolicy=byfile");
            // Collections.addAll(list, "-processorpath", Lib.ERROR_PRONE.toString());
            // Collections.addAll(list, "-Xplugin:ErrorProne -XepAllErrorsAsWarnings --illegal-access=warn");
        }

        return list;
    }

    /** Create a task that compiles a single file */
    private JavacTask singleFileTask(URI file, String contents) {
        diags.clear();
        return (JavacTask)
                compiler.getTask(
                        null,
                        fileManager,
                        diags::add,
                        options(sourcePath, classPath, false),
                        Collections.emptyList(),
                        Collections.singletonList(new StringFileObject(contents, file)));
    }

    private JavacTask batchTask(Collection<Path> paths, boolean lint) {
        diags.clear();
        var files = paths.stream().map(Path::toFile).collect(Collectors.toList());
        return (JavacTask)
                compiler.getTask(
                        null,
                        fileManager,
                        diags::add,
                        options(sourcePath, classPath, lint),
                        Collections.emptyList(),
                        fileManager.getJavaFileObjectsFromFiles(files));
    }

    private Collection<Path> removeModuleInfo(Collection<Path> files) {
        var result = new ArrayList<Path>();
        for (var f : files) {
            if (f.getFileName().endsWith("module-info.java"))
                LOG.info("Skip " + f);
            else 
                result.add(f);
        }
        return result;
    }

    List<Diagnostic<? extends JavaFileObject>> lint(Collection<Path> files) {
        files = removeModuleInfo(files);
        if (files.isEmpty()) return Collections.emptyList();
        
        var task = batchTask(files, true);
        try {
            task.parse();
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Collections.unmodifiableList(new ArrayList<>(diags));
    }

    class Profiler implements TaskListener {
        Map<TaskEvent.Kind, Instant> started = new EnumMap<>(TaskEvent.Kind.class);
        Map<TaskEvent.Kind, Duration> profile = new EnumMap<>(TaskEvent.Kind.class);

        @Override
        public void started(TaskEvent e) {
            started.put(e.getKind(), Instant.now());
        }

        @Override
        public void finished(TaskEvent e) {
            var k = e.getKind();
            var start = started.getOrDefault(k, Instant.now());
            var elapsed = Duration.between(start, Instant.now());
            var soFar = profile.getOrDefault(k, Duration.ZERO);
            var total = soFar.plus(elapsed);
            profile.put(k, total);
        }

        void print() {
            var lines = new StringJoiner("\n\t");
            for (var k : TaskEvent.Kind.values()) {
                var elapsed = profile.getOrDefault(k, Duration.ZERO);
                var ms = elapsed.getSeconds() * 1000 + elapsed.getNano() / 1000 / 1000;
                lines.add(String.format("%s: %dms", k, ms));
            }
            LOG.info("Compilation profile:\n\t" + lines);
        }
    };

    /** Stores the compiled version of a single file */
    class Cache {
        final String contents;
        final URI file;
        final CompilationUnitTree root;
        final JavacTask task;
        final int line, character;

        Cache(URI file, String contents, int line, int character) {
            // If `line` is -1, recompile the entire file
            if (line == -1) {
                this.contents = contents;
            }
            // Otherwise, focus on the block surrounding line:character,
            // erasing all other block bodies and everything after the cursor in its own block
            else {
                var p = new Pruner(file, contents);
                p.prune(line, character);
                this.contents = p.contents();
            }
            this.file = file;
            this.task = singleFileTask(file, this.contents);
            this.line = line;
            this.character = character;
            
            var profiler = new Profiler();
            task.addTaskListener(profiler);
            try {
                var it = task.parse().iterator();
                this.root = it.hasNext() ? it.next() : null;
                // The results of task.analyze() are unreliable when errors are present
                // You can get at `Element` values using `Trees`
                task.analyze();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            profiler.print();
        }
    }

    /** Recompile if the active file has been edited, or if the active file has changed */
    private void recompile(URI file, String contents, int line, int character) {
        if (cache == null
                || !cache.file.equals(file)
                || !cache.contents.equals(contents)
                || cache.line != line
                || cache.character != character) {
            cache = new Cache(file, contents, line, character);
        }
    }

    /** Find the smallest tree that includes the cursor */
    private TreePath path(URI file, int line, int character) {
        var trees = Trees.instance(cache.task);
        var pos = trees.getSourcePositions();
        var cursor = cache.root.getLineMap().getPosition(line, character);

        // Search for the smallest element that encompasses line:column
        class FindSmallest extends TreePathScanner<Void, Void> {
            TreePath found = null;

            boolean containsCursor(Tree tree) {
                long start = pos.getStartPosition(cache.root, tree), end = pos.getEndPosition(cache.root, tree);
                // If element has no position, give up
                if (start == -1 || end == -1) return false;
                // Check if `tree` contains line:column
                return start <= cursor && cursor <= end;
            }

            @Override
            public Void scan(Tree tree, Void nothing) {
                // This is pre-order traversal, so the deepest element will be the last one remaining in `found`
                if (containsCursor(tree)) found = new TreePath(getCurrentPath(), tree);
                super.scan(tree, nothing);
                return null;
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void nothing) {
                for (var t : node.getErrorTrees()) {
                    scan(t, nothing);
                }
                return null;
            }

            TreePath find(Tree root) {
                scan(root, null);
                if (found == null) {
                    var message = String.format("No TreePath to %s %d:%d", file, line, character);
                    throw new RuntimeException(message);
                }
                return found;
            }
        }
        return new FindSmallest().find(cache.root);
    }

    private List<ExecutableElement> virtualMethods(DeclaredType type) {
        var result = new ArrayList<ExecutableElement>();
        for (var member : type.asElement().getEnclosedElements()) {
            if (member instanceof ExecutableElement) {
                var method = (ExecutableElement) member;
                if (!method.getSimpleName().contentEquals("<init>") && !method.getModifiers().contains(Modifier.STATIC)) {
                    result.add(method);
                }
            }
        }
        return result;
    }

    private TypeMirror enclosingClass(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        var trees = Trees.instance(cache.task);
        var path = path(file, line, character);
        while (!(path.getLeaf() instanceof ClassTree)) path = path.getParentPath();
        var enclosingClass = trees.getElement(path);

        return enclosingClass.asType();
    }

    private List<ExecutableElement> thisMethods(URI file, String contents, int line, int character) {
        var thisType = enclosingClass(file, contents, line, character);
        var types = cache.task.getTypes();
        var result = new ArrayList<ExecutableElement>();

        if (thisType instanceof DeclaredType) {
            var type = (DeclaredType) thisType;
            result.addAll(virtualMethods(type));
        }
        
        return result;
    }

    private void collectSuperMethods(TypeMirror thisType, List<ExecutableElement> result) {
        var types = cache.task.getTypes();

        for (var superType : types.directSupertypes(thisType)) {
            if (superType instanceof DeclaredType) {
                var type = (DeclaredType) superType;
                result.addAll(virtualMethods(type));
                collectSuperMethods(type, result);
            }
        }
    }

    private List<ExecutableElement> superMethods(URI file, String contents, int line, int character) {
        var thisType = enclosingClass(file, contents, line, character);
        var result = new ArrayList<ExecutableElement>();

        collectSuperMethods(thisType, result);

        return result;
    }

    /** Find all identifiers in scope at line:character */
    public List<Element> scopeMembers(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        var trees = Trees.instance(cache.task);
        var types = cache.task.getTypes();
        var path = path(file, line, character);
        var start = trees.getScope(path);

        class Walk {
            List<Element> result = new ArrayList<>();

            boolean isStatic(Scope s) {
                var method = s.getEnclosingMethod();
                if (method != null) {
                    return method.getModifiers().contains(Modifier.STATIC);
                } else return false;
            }

            boolean isStatic(Element e) {
                return e.getModifiers().contains(Modifier.STATIC);
            }

            boolean isThisOrSuper(VariableElement ve) {
                var name = ve.getSimpleName();
                return name.contentEquals("this") || name.contentEquals("super");
            }

            // Place each member of `this` or `super` directly into `results`
            void unwrapThisSuper(VariableElement ve) {
                var thisType = ve.asType();
                // `this` and `super` should always be instances of DeclaredType, which we'll use to check accessibility
                if (!(thisType instanceof DeclaredType)) {
                    LOG.warning(String.format("%s is not a DeclaredType", thisType));
                    return;
                }
                var thisDeclaredType = (DeclaredType) thisType;
                var thisElement = types.asElement(thisDeclaredType);
                for (var thisMember : thisElement.getEnclosedElements()) {
                    if (isStatic(start) && !isStatic(thisMember)) continue;
                    if (thisMember.getSimpleName().contentEquals("<init>")) continue;

                    // Check if member is accessible from original scope
                    if (trees.isAccessible(start, thisMember, thisDeclaredType)) {
                        result.add(thisMember);
                    }
                }
            }

            // Place each member of `s` into results, and unwrap `this` and `super`
            void walkLocals(Scope s) {
                try {
                    for (var e : s.getLocalElements()) {
                        if (e instanceof TypeElement) {
                            var te = (TypeElement) e;
                            if (trees.isAccessible(start, te)) result.add(te);
                        } else if (e instanceof VariableElement) {
                            var ve = (VariableElement) e;
                            if (isThisOrSuper(ve)) {
                                unwrapThisSuper(ve);
                                if (!isStatic(s)) result.add(ve);
                            } else {
                                result.add(ve);
                            }
                        } else {
                            result.add(e);
                        }
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "error walking locals in scope", e);
                }
            }

            // Walk each enclosing scope, placing its members into `results`
            List<Element> walkScopes() {
                for (var s = start; s != null; s = s.getEnclosingScope()) {
                    walkLocals(s);
                }

                return result;
            }
        }
        return new Walk().walkScopes();
    }

    private List<TypeMirror> supersWithSelf(TypeMirror t) {
        var elements = cache.task.getElements();
        var types = cache.task.getTypes();
        var result = new ArrayList<TypeMirror>();
        result.add(t);
        // Add members of superclasses and interfaces
        result.addAll(types.directSupertypes(t));
        // Object type is not included by default
        // We need to add it to get members like .equals(other) and .hashCode()
        // TODO this may add things twice for interfaces with no super-interfaces
        result.add(elements.getTypeElement("java.lang.Object").asType());
        return result;
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

    /** Find all case options in the switch expression surrounding line:character */
    private List<Completion> cases(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        // Find path to case
        var trees = Trees.instance(cache.task);
        var types = cache.task.getTypes();
        var path = path(file, line, character);
        // Find surrounding switch
        while (!(path.getLeaf() instanceof SwitchTree))
            path = path.getParentPath();
        var leaf = (SwitchTree) path.getLeaf();
        path = new TreePath(path, leaf.getExpression());
        // Get members of switched type
        var element = trees.getElement(path);
        var type = element.asType();
        var definition = types.asElement(type);
        var result = new ArrayList<Completion>();
        for (var member : definition.getEnclosedElements()) {
            if (member.getKind() == ElementKind.ENUM_CONSTANT)
                result.add(Completion.ofElement(member));
        }

        return result;
    }

    /** Find all members of expression ending at line:character */
    public List<Completion> members(URI file, String contents, int line, int character, boolean isReference) {
        recompile(file, contents, line, character);

        var trees = Trees.instance(cache.task);
        var types = cache.task.getTypes();
        var path = path(file, line, character);
        var scope = trees.getScope(path);
        var element = trees.getElement(path);

        if (element instanceof PackageElement) {
            var result = new ArrayList<Completion>();
            var p = (PackageElement) element;

            // Add class-names resolved as Element by javac
            for (var member : p.getEnclosedElements()) {
                // If the package member is a TypeElement, like a class or interface, check if it's accessible
                if (member instanceof TypeElement) {
                    if (trees.isAccessible(scope, (TypeElement) member)) {
                        result.add(Completion.ofElement(member));
                    }
                }
                // Otherwise, just assume it's accessible and add it to the list
                else result.add(Completion.ofElement(member));
            }
            // Add sub-package names resolved as String by guava ClassPath
            var parent = p.getQualifiedName().toString();
            var subs = subPackages(parent);
            for (var sub : subs) {
                result.add(Completion.ofPackagePart(sub, Parser.lastName(sub)));
            }

            return result;
        } else if (element instanceof TypeElement && isReference) {
            var result = new ArrayList<Completion>();
            var t = (TypeElement) element;

            // Add members
            for (var member : t.getEnclosedElements()) {
                if (member.getKind() == ElementKind.METHOD && trees.isAccessible(scope, member, (DeclaredType) t.asType())) {
                    result.add(Completion.ofElement(member));
                }
            }

            // Add ::new
            result.add(Completion.ofKeyword("new"));

            return result;
        } else if (element instanceof TypeElement && !isReference) {
            var result = new ArrayList<Completion>();
            var t = (TypeElement) element;

            // Add static members
            for (var member : t.getEnclosedElements()) {
                // TODO if this is a member reference :: then include non-statics
                if (member.getModifiers().contains(Modifier.STATIC)
                        && trees.isAccessible(scope, member, (DeclaredType) t.asType())) {
                    result.add(Completion.ofElement(member));
                }
            }

            // Add .class
            result.add(Completion.ofKeyword("class"));
            result.add(Completion.ofKeyword("this"));
            result.add(Completion.ofKeyword("super"));

            return result;
        } else {
            var type = trees.getTypeMirror(path);
            if (hasMembers(type)) {
                var result = new ArrayList<Completion>();
                var ts = supersWithSelf(type);
                var alreadyAdded = new HashSet<String>();
                for (var t : ts) {
                    var e = types.asElement(t);
                    if (e != null) {
                        for (var member : e.getEnclosedElements()) {
                            // Don't add statics
                            if (member.getModifiers().contains(Modifier.STATIC)) continue;
                            // Don't add constructors
                            if (member.getSimpleName().contentEquals("<init>")) continue;
                            // Skip overridden members from superclass
                            if (alreadyAdded.contains(member.toString())) continue;

                            // If type is a DeclaredType, check accessibility of member
                            if (t instanceof DeclaredType) {
                                if (trees.isAccessible(scope, member, (DeclaredType) t)) {
                                    result.add(Completion.ofElement(member));
                                }
                            }
                            // Otherwise, accessibility rules are very complicated
                            // Give up and just declare that everything is accessible
                            else result.add(Completion.ofElement(member));
                            // Remember the signature of the added method, so we don't re-add it later
                            alreadyAdded.add(member.toString());
                        }
                    }
                    
                    if (t instanceof ArrayType) {
                        result.add(Completion.ofKeyword("length"));
                    }
                }
                return result;
            } else {
                LOG.warning("Don't know how to complete members of type " + type);
                return Collections.emptyList();
            }
        }
    }

    private static String[] TOP_LEVEL_KEYWORDS = {
        "package",
        "import",
        "public",
        "private",
        "protected",
        "abstract",
        "class",
        "interface",
        "implements",
    };

    private static String[] CLASS_BODY_KEYWORDS = {
        "public",
        "private",
        "protected",
        "static",
        "final",
        "native",
        "synchronized",
        "abstract",
        "default",
        "class",
        "interface",
        "void",
        "boolean",
        "int",
        "long",
        "float",
        "double",
    };

    private static String[] METHOD_BODY_KEYWORDS = {
        "new",
        "assert",
        "try",
        "catch",
        "finally",
        "throw",
        "return",
        "break",
        "case",
        "continue",
        "default",
        "do",
        "while",
        "for",
        "switch",
        "if",
        "else",
        "instanceof",
        "var",
        "final",
        "class",
        "void",
        "boolean",
        "int",
        "long",
        "float",
        "double",
    };

    /**
     * Complete members or identifiers at the cursor. Delegates to `members` or `scopeMembers`, depending on whether the
     * expression before the cursor looks like `foo.bar` or `foo`
     */
    public CompletionResult completions(URI file, String contents, int line, int character, int limitHint) {
        var started = Instant.now();
        LOG.info(String.format("Completing at %s[%d,%d]...", file.getPath(), line, character));
        
        // TODO why not just recompile? It's going to get triggered shortly anyway
        var task = singleFileTask(file, contents);
        CompilationUnitTree parse;
        try {
            parse = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var pos = Trees.instance(task).getSourcePositions();
        var lines = parse.getLineMap();
        var cursor = lines.getPosition(line, character);

        class Find extends TreeScanner<Void, Void> {
            List<Completion> result = null;
            boolean isIncomplete = false;
            int insideClass = 0, insideMethod = 0;

            boolean containsCursor(Tree node) {
                return pos.getStartPosition(parse, node) <= cursor && cursor <= pos.getEndPosition(parse, node);
            }

            @Override
            public Void visitClass(ClassTree node, Void nothing) {
                insideClass++;
                super.visitClass(node, null);
                insideClass--;
                return null;
            }

            @Override
            public Void visitMethod(MethodTree node, Void nothing) {
                insideMethod++;
                super.visitMethod(node, null);
                insideMethod--;
                return null;
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void nothing) {
                super.visitMemberSelect(node, nothing);

                if (containsCursor(node) && !containsCursor(node.getExpression()) && result == null) {
                    LOG.info("...completing members of " + node.getExpression());
                    long offset = pos.getEndPosition(parse, node.getExpression()),
                            line = lines.getLineNumber(offset),
                            column = lines.getColumnNumber(offset);
                    result = members(file, contents, (int) line, (int) column, false);
                }
                return null;
            }

            @Override
            public Void visitMemberReference(MemberReferenceTree node, Void nothing) {
                super.visitMemberReference(node, nothing);

                if (containsCursor(node) && !containsCursor(node.getQualifierExpression()) && result == null) {
                    LOG.info("...completing members of " + node.getQualifierExpression());
                    long offset = pos.getEndPosition(parse, node.getQualifierExpression()),
                            line = lines.getLineNumber(offset),
                            column = lines.getColumnNumber(offset);
                    result = members(file, contents, (int) line, (int) column, true);
                }
                return null;
            }

            @Override
            public Void visitCase(CaseTree node, Void nothing) {
                var containsCursor = containsCursor(node);
                for (var s : node.getStatements()) {
                    if (containsCursor(s))
                        containsCursor = false;
                }

                if (containsCursor) {
                    long offset = pos.getEndPosition(parse, node.getExpression()),
                            line = lines.getLineNumber(offset),
                            column = lines.getColumnNumber(offset);
                    result = cases(file, contents, (int) line, (int) column);
                } else {
                    super.visitCase(node, nothing);
                }
                return null;
            }

            @Override
            public Void visitAnnotation(AnnotationTree node, Void nothing) {
                if (containsCursor(node.getAnnotationType())) {
                    LOG.info("...completing annotation");
                    result = new ArrayList<>();
                    var id = (IdentifierTree) node.getAnnotationType();
                    var partialName = Objects.toString(id.getName(), "");
                    // Add @Override ... snippet
                    if ("Override".startsWith(partialName)) {
                        // TODO filter out already-implemented methods using thisMethods
                        var alreadyShown = new HashSet<String>();
                        for (var method : superMethods(file, contents, line, character)) {
                            var mods = method.getModifiers();
                            if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.PRIVATE)) continue;
                            
                            var label = "@Override " + ShortTypePrinter.printMethod(method);
                            var snippet = "Override\n" + new TemplatePrinter().printMethod(method) + " {\n    $0\n}";
                            var override = Completion.ofSnippet(label, snippet);
                            if (!alreadyShown.contains(label)) {
                                result.add(override);
                                alreadyShown.add(label);
                            }
                        }
                    }
                    // Add @Override, @Test, other simple class names
                    completeScopeIdentifiers(partialName);
                } else {
                    super.visitAnnotation(node, nothing);
                }
                return null;
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void nothing) {
                super.visitIdentifier(node, nothing);

                if (containsCursor(node) && result == null) {
                    LOG.info("...completing identifiers");
                    result = new ArrayList<>();

                    // Add snippets
                    if (insideClass == 0) {
                        // If no package declaration is present, suggest package [inferred name];
                        if (parse.getPackage() == null) {
                            relativeToSourcePath(file).ifPresent(relative -> {
                                var name = relative.toString().replace(File.separatorChar, '.');
                                result.add(Completion.ofSnippet("package " + name, "package " + name + ";\n\n"));
                            });
                        }
                        // If no class declaration is present, suggest class [file name]
                        var hasClassDeclaration = false;
                        for (var t : parse.getTypeDecls()) {
                            if (!(t instanceof ErroneousTree)) {
                                hasClassDeclaration = true;
                            }
                        }
                        if (!hasClassDeclaration) {
                            var name = Paths.get(file).getFileName().toString();
                            name = name.substring(0, name.length() - ".java".length());
                            result.add(Completion.ofSnippet("class " + name, "class " + name + " {\n    $0\n}"));
                        }
                    }
                    // Add identifiers
                    var partialName = Objects.toString(node.getName(), "");
                    completeScopeIdentifiers(partialName);
                    // Add keywords
                    if (insideClass == 0) {
                        addKeywords(TOP_LEVEL_KEYWORDS, partialName);
                    }
                    else if (insideMethod == 0) {
                        addKeywords(CLASS_BODY_KEYWORDS, partialName);
                    }
                    else {
                        addKeywords(METHOD_BODY_KEYWORDS, partialName);
                    }
                }
                return null;
            }

            private void addKeywords(String[] keywords, String partialName) {
                for (var k : keywords) {
                    if (k.startsWith(partialName)) {
                        result.add(Completion.ofKeyword(k));
                    }
                }
            }

            private void completeScopeIdentifiers(String partialName) {
                var startsWithUpperCase = partialName.length() > 0 && Character.isUpperCase(partialName.charAt(0));
                var alreadyImported = new HashSet<String>();
                // Add names that have already been imported
                for (var m : scopeMembers(file, contents, line, character)) {
                    if (m.getSimpleName().toString().startsWith(partialName)) {
                        result.add(Completion.ofElement(m));

                        if (m instanceof TypeElement && startsWithUpperCase) {
                            var t = (TypeElement) m;
                            alreadyImported.add(t.getQualifiedName().toString());
                        }
                    }
                }
                // Add names of classes that haven't been imported
                if (startsWithUpperCase) {
                    var packageName = Objects.toString(parse.getPackageName(), "");
                    BooleanSupplier full = () -> {
                        if (result.size() >= limitHint) {
                            isIncomplete = true;
                            return true;
                        } 
                        return false;
                    };
                    Predicate<String> matchesPartialName =
                            qualifiedName -> {
                                var className = Parser.lastName(qualifiedName);
                                return className.startsWith(partialName);
                            };
                    Predicate<String> notAlreadyImported = className -> !alreadyImported.contains(className);
                    for (var c : jdkClasses.classes()) {
                        if (full.getAsBoolean()) return;
                        if (matchesPartialName.test(c) && notAlreadyImported.test(c) && jdkClasses.isAccessibleFromPackage(c, packageName)) {
                            result.add(Completion.ofNotImportedClass(c));
                        }
                    }
                    for (var c : classPathClasses.classes()) {
                        if (full.getAsBoolean()) return;
                        if (matchesPartialName.test(c) && notAlreadyImported.test(c) && classPathClasses.isAccessibleFromPackage(c, packageName)) {
                            result.add(Completion.ofNotImportedClass(c));
                        }
                    }
                    Predicate<Path> matchesFileName = 
                            file -> file.getFileName().toString().startsWith(partialName);
                    Predicate<Path> isPublic = 
                        file -> {
                            var fileName = file.getFileName().toString();
                            if (!fileName.endsWith(".java")) return false;
                            var simpleName = fileName.substring(0, fileName.length() - ".java".length());
                            Stream<String> lines;
                            try {
                                lines = Files.lines(file);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return lines.anyMatch(line -> line.matches(".*public\\s+class\\s+" + simpleName + ".*"));
                        };
                    for (var dir : sourcePath) {
                        Function<Path, String> qualifiedName = 
                                file -> {
                                    var relative = dir.relativize(file).toString().replace('/', '.');
                                    if (!relative.endsWith(".java")) return "??? " + relative + " does not end in .java";
                                    return relative.substring(0, relative.length() - ".java".length());
                                };
                        for (var file : javaSourcesInDir(dir)) {
                            if (full.getAsBoolean()) return;
                            // Fast check, file name only
                            if (matchesFileName.test(file)) {
                                var c = qualifiedName.apply(file);
                                // Slow check, open file
                                if (matchesPartialName.test(c) && notAlreadyImported.test(c) && isPublic.test(file)) {
                                    result.add(Completion.ofNotImportedClass(c));
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void nothing) {
                for (var t : node.getErrorTrees()) {
                    t.accept(this, null);
                }
                return null;
            }

            CompletionResult run() {
                scan(parse, null);
                if (result == null) {
                    result = new ArrayList<>();
                    addKeywords(TOP_LEVEL_KEYWORDS, "");
                }
                if (isIncomplete) LOG.info(String.format("Found %d items (incomplete)", result.size()));
                return new CompletionResult(result, isIncomplete);
            }
        }
        var result = new Find().run();
        
        LOG.info(String.format("...completed in %d ms", Duration.between(started, Instant.now()).toMillis()));
        return result;
    }

    /** Find all overloads for the smallest method call that includes the cursor */
    public Optional<MethodInvocation> methodInvocation(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        var trees = Trees.instance(cache.task);
        var start = path(file, line, character);

        for (var path = start; path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof MethodInvocationTree) {
                var invoke = (MethodInvocationTree) path.getLeaf();
                var method = trees.getElement(trees.getPath(path.getCompilationUnit(), invoke.getMethodSelect()));
                var results = new ArrayList<ExecutableElement>();
                for (var m : method.getEnclosingElement().getEnclosedElements()) {
                    if (m.getKind() == ElementKind.METHOD && m.getSimpleName().equals(method.getSimpleName())) {
                        results.add((ExecutableElement) m);
                    }
                }
                var activeParameter = invoke.getArguments().indexOf(start.getLeaf());
                Optional<ExecutableElement> activeMethod =
                        method instanceof ExecutableElement
                                ? Optional.of((ExecutableElement) method)
                                : Optional.empty();
                return Optional.of(new MethodInvocation(invoke, activeMethod, activeParameter, results));
            } else if (path.getLeaf() instanceof NewClassTree) {
                var invoke = (NewClassTree) path.getLeaf();
                var method = trees.getElement(path);
                var results = new ArrayList<ExecutableElement>();
                for (var m : method.getEnclosingElement().getEnclosedElements()) {
                    if (m.getKind() == ElementKind.CONSTRUCTOR) {
                        results.add((ExecutableElement) m);
                    }
                }
                var activeParameter = invoke.getArguments().indexOf(start.getLeaf());
                Optional<ExecutableElement> activeMethod =
                        method instanceof ExecutableElement
                                ? Optional.of((ExecutableElement) method)
                                : Optional.empty();
                return Optional.of(new MethodInvocation(invoke, activeMethod, activeParameter, results));
            }
        }
        return Optional.empty();
    }

    /** Find the smallest element that includes the cursor */
    public Element element(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        var trees = Trees.instance(cache.task);
        var path = path(file, line, character);
        return trees.getElement(path);
    }

    private Optional<TypeElement> topLevelDeclaration(Element e) {
        var parent = e;
        TypeElement result = null;
        while (parent.getEnclosingElement() != null) {
            if (parent instanceof TypeElement) result = (TypeElement) parent;
            parent = parent.getEnclosingElement();
        }
        return Optional.ofNullable(result);
    }

    /** */
    private boolean containsTopLevelDeclaration(Path file, String simpleClassName) {
        var find = Pattern.compile("\\b(class|interface|enum) +" + simpleClassName + "\\b");
        try (var lines = Files.newBufferedReader(file)) {
            var line = lines.readLine();
            while (line != null) {
                if (find.matcher(line).find()) return true;
                line = lines.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    /** Find the file `e` was declared in */
    private Optional<Path> findDeclaringFile(TypeElement e) {
        var name = e.getQualifiedName().toString();
        var lastDot = name.lastIndexOf('.');
        var packageName = lastDot == -1 ? "" : name.substring(0, lastDot);
        var className = name.substring(lastDot + 1);
        // First, look for a file named [ClassName].java
        var packagePath = Paths.get(packageName.replace('.', File.separatorChar));
        var publicClassPath = packagePath.resolve(className + ".java");
        for (var root : sourcePath) {
            var absPath = root.resolve(publicClassPath);
            if (Files.exists(absPath) && containsTopLevelDeclaration(absPath, className)) {
                return Optional.of(absPath);
            }
        }
        // Then, look for a secondary declaration in all java files in the package
        var isPublic = e.getModifiers().contains(Modifier.PUBLIC);
        if (!isPublic) {
            for (var root : sourcePath) {
                var absDir = root.resolve(packagePath);
                try {
                    var foundFile =
                            Files.list(absDir).filter(f -> containsTopLevelDeclaration(f, className)).findFirst();
                    if (foundFile.isPresent()) return foundFile;
                } catch (IOException err) {
                    throw new RuntimeException(err);
                }
            }
        }
        return Optional.empty();
    }

    /** Compile `file` and locate `e` in it */
    private Optional<TreePath> findIn(Element e, Path file, String contents) {
        var task = singleFileTask(file.toUri(), contents);
        CompilationUnitTree tree;
        try {
            tree = task.parse().iterator().next();
            task.analyze();
        } catch (IOException err) {
            throw new RuntimeException(err);
        }
        var trees = Trees.instance(task);
        class Find extends TreePathScanner<Void, Void> {
            Optional<TreePath> found = Optional.empty();

            boolean toStringEquals(Object left, Object right) {
                return Objects.equals(Objects.toString(left, ""), Objects.toString(right, ""));
            }

            /** Check if the declaration at the current path is the same symbol as `e` */
            boolean sameSymbol() {
                var candidate = trees.getElement(getCurrentPath());
                // `e` is from a different compilation, so we have to compare qualified names
                return toStringEquals(candidate.getEnclosingElement(), e.getEnclosingElement())
                        && toStringEquals(candidate, e);
            }

            void check() {
                if (sameSymbol()) {
                    found = Optional.of(getCurrentPath());
                }
            }

            @Override
            public Void visitClass(ClassTree node, Void aVoid) {
                check();
                return super.visitClass(node, aVoid);
            }

            @Override
            public Void visitMethod(MethodTree node, Void aVoid) {
                check();
                return super.visitMethod(node, aVoid);
            }

            @Override
            public Void visitVariable(VariableTree node, Void aVoid) {
                check();
                return super.visitVariable(node, aVoid);
            }

            Optional<TreePath> run() {
                scan(tree, null);
                return found;
            }
        }
        return new Find().run();
    }

    public Optional<TreePath> definition(URI file, int line, int character, Function<URI, String> contents) {
        recompile(file, contents.apply(file), line, character);

        var trees = Trees.instance(cache.task);
        var path = path(file, line, character);
        LOG.info("Looking for definition for " + path.getLeaf() + "...");
        var e = trees.getElement(path);
        var declaration = topLevelDeclaration(e);
        LOG.info("...looking for top-level declaration " + declaration);
        var declaringFile = declaration.flatMap(this::findDeclaringFile);
        LOG.info("...declaration is in " + declaringFile);
        return declaringFile.flatMap(f -> findIn(e, f, contents.apply(f.toUri())));
    }

    /** Look up the javadoc associated with `method` */
    public Optional<DocCommentTree> methodDoc(ExecutableElement method) {
        var classElement = (TypeElement) method.getEnclosingElement();
        var className = classElement.getQualifiedName().toString();
        var methodName = method.getSimpleName().toString();
        return docs.memberDoc(className, methodName);
    }

    /** Find and parse the source code associated with `method` */
    public Optional<MethodTree> methodTree(ExecutableElement method) {
        var classElement = (TypeElement) method.getEnclosingElement();
        var className = classElement.getQualifiedName().toString();
        var methodName = method.getSimpleName().toString();
        var parameterTypes = method.getParameters().stream().map(p -> p.asType().toString()).collect(Collectors.toList());
        return docs.findMethod(className, methodName, parameterTypes);
    }

    /** Look up the javadoc associated with `type` */
    public Optional<DocCommentTree> classDoc(TypeElement type) {
        return docs.classDoc(type.getQualifiedName().toString());
    }

    public Optional<DocCommentTree> classDoc(String qualifiedName) {
        return docs.classDoc(qualifiedName);
    }

    private Iterable<Path> javaSourcesInDir(Path dir) {
        var match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        try {
            // TODO instead of looking at EVERY file, once you see a few files with the same source directory,
            // ignore all subsequent files in the directory
            return Files.walk(dir).filter(java -> match.matches(java.getFileName()))::iterator;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> potentialReferences(Element to, ReportReferencesProgress progress) {
        var name = to.getSimpleName().toString();
        var word = Pattern.compile("\\b\\w+\\b");
        var result = new ArrayList<Path>();
        int nScanned = 0;
        Predicate<String> containsWord =
                line -> {
                    Matcher m = word.matcher(line);
                    while (m.find()) {
                        if (m.group().equals(name)) return true;
                    }
                    return false;
                };
        var allFiles = new ArrayList<Path>();
        for (var dir : sourcePath) {
            for (var file : javaSourcesInDir(dir)) {
                allFiles.add(file);
            }
        }
        progress.scanForPotentialReferences(0, allFiles.size());
        for (var file : allFiles) {
            try {
                if (Files.lines(file).anyMatch(containsWord)) 
                    result.add(file);
                nScanned++;
                progress.scanForPotentialReferences(nScanned, allFiles.size());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    /**
     * Represents a batch compilation of many files. The batch context is different that the incremental context, so
     * methods in this class should not access `cache`.
     */
    static class Batch {
        final JavacTask task;
        final List<CompilationUnitTree> roots;

        Batch(JavacTask task, List<CompilationUnitTree> roots) {
            this.task = task;
            this.roots = roots;
        }

        private boolean toStringEquals(Object left, Object right) {
            return Objects.equals(Objects.toString(left, ""), Objects.toString(right, ""));
        }

        private boolean sameSymbol(Element target, Element symbol) {
            return symbol != null
                    && target != null
                    && toStringEquals(symbol.getEnclosingElement(), target.getEnclosingElement())
                    && toStringEquals(symbol, target);
        }

        private List<TreePath> actualReferences(CompilationUnitTree from, Element to) {
            var trees = Trees.instance(task);

            class Finder extends TreeScanner<Void, Void> {
                List<TreePath> results = new ArrayList<>();

                @Override
                public Void scan(Tree leaf, Void nothing) {
                    if (leaf != null) {
                        var path = trees.getPath(from, leaf);
                        var found = trees.getElement(path);

                        if (sameSymbol(found, to)) results.add(path);
                        else super.scan(leaf, nothing);
                    }
                    return null;
                }

                List<TreePath> run() {
                    scan(from, null);
                    return results;
                }
            }
            return new Finder().run();
        }
    }

    private Batch compileBatch(List<Path> files) {
        var task = batchTask(files, false);
        var result = new ArrayList<CompilationUnitTree>();
        var profiler = new Profiler();
        task.addTaskListener(profiler);
        try {
            for (var t : task.parse()) result.add(t);
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        profiler.print();

        return new Batch(task, result);
    }

    public List<TreePath> references(URI file, String contents, int line, int character, ReportReferencesProgress progress) {
        recompile(file, contents, -1, -1);

        var trees = Trees.instance(cache.task);
        var path = path(file, line, character);
        // It's sort of odd that this works
        // `to` is part of a different batch than `batch = compileBatch(possible)`,
        // so `to.equals(...thing from batch...)` shouldn't work
        var to = trees.getElement(path);
        var possible = potentialReferences(to, progress);
        progress.checkPotentialReferences(0, possible.size());
        // TODO optimize by pruning method bodies that don't contain potential references
        var batch = compileBatch(possible);
        var result = new ArrayList<TreePath>();
        int nChecked = 0;
        for (var f : batch.roots) {
            result.addAll(batch.actualReferences(f, to));
            nChecked++;
            progress.checkPotentialReferences(nChecked, possible.size());
        }
        return result;
    }

    public Stream<TreePath> findSymbols(String query) {
        return sourcePath.stream().flatMap(dir -> Parser.findSymbols(dir, query));
    }

    // TODO this is ugly, suggests something needs to be moved into JavaCompilerService
    public Trees trees() {
        return Trees.instance(cache.task);
    }

    /**
     * Figure out what imports this file should have. Star-imports like `import java.util.*` are converted to individual
     * class imports. Missing imports are inferred by looking at imports in other source files.
     */
    public FixImports fixImports(URI file, String contents) {
        LOG.info("Fix imports in " + file);
        // Compile a single file
        var task = singleFileTask(file, contents);
        CompilationUnitTree tree;
        try {
            tree = task.parse().iterator().next();
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Check diagnostics for missing imports
        var unresolved = new HashSet<String>();
        for (var d : diags) {
            if (d.getCode().equals("compiler.err.cant.resolve.location") && d.getSource().toUri().equals(file)) {
                long start = d.getStartPosition(), end = d.getEndPosition();
                var id = contents.substring((int) start, (int) end);
                if (id.matches("[A-Z]\\w+")) {
                    unresolved.add(id);
                } else LOG.warning(id + " doesn't look like a class");
            } else if (d.getMessage(null).contains("cannot find symbol")) {
                var lines = d.getMessage(null).split("\n");
                var firstLine = lines.length > 0 ? lines[0] : "";
                LOG.warning(String.format("%s %s doesn't look like symbol-not-found", d.getCode(), firstLine));
            }
        }
        // Look at imports in other classes to help us guess how to fix imports
        var sourcePathImports = Parser.existingImports(sourcePath);
        var classes = new HashSet<String>();
        classes.addAll(jdkClasses.classes());
        classes.addAll(classPathClasses.classes());
        var fixes = Parser.resolveSymbols(unresolved, sourcePathImports, classes);
        // Figure out which existing imports are actually used
        var trees = Trees.instance(task);
        var references = new HashSet<String>();
        class FindUsedImports extends TreePathScanner<Void, Void> {
            @Override
            public Void visitIdentifier(IdentifierTree node, Void nothing) {
                var e = trees.getElement(getCurrentPath());
                if (e instanceof TypeElement) {
                    var t = (TypeElement) e;
                    var qualifiedName = t.getQualifiedName().toString();
                    var lastDot = qualifiedName.lastIndexOf('.');
                    var packageName = lastDot == -1 ? "" : qualifiedName.substring(0, lastDot);
                    var thisPackage = Objects.toString(tree.getPackageName(), "");
                    // java.lang.* and current package are imported by default
                    if (!packageName.equals("java.lang")
                            && !packageName.equals(thisPackage)
                            && !packageName.equals("")) {
                        references.add(qualifiedName);
                    }
                }
                return null;
            }
        }
        new FindUsedImports().scan(tree, null);
        // Take the intersection of existing imports ^ existing identifiers
        var qualifiedNames = new HashSet<String>();
        for (var i : tree.getImports()) {
            var imported = i.getQualifiedIdentifier().toString();
            if (imported.endsWith(".*")) {
                var packageName = Parser.mostName(imported);
                var isUsed = references.stream().anyMatch(r -> r.startsWith(packageName));
                if (isUsed) qualifiedNames.add(imported);
                else LOG.warning("There are no references to package " + imported);
            }
            else {
                if (references.contains(imported)) qualifiedNames.add(imported);
                else LOG.warning("There are no references to class " + imported);
            }
        }
        // Add qualified names from fixes
        qualifiedNames.addAll(fixes.values());
        return new FixImports(tree, trees.getSourcePositions(), qualifiedNames);
    }

    public List<TestMethod> testMethods(URI file, String contents) {
        LOG.info(String.format("Finding test methods in %s", file.getPath()));

        var task = singleFileTask(file, contents);
        CompilationUnitTree parse;
        try {
            parse = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        class Find extends TreeScanner<Void, Void> {
            ClassTree enclosingClass = null;
            Set<ClassTree> testClasses = new HashSet<>();
            List<TestMethod> found = new ArrayList<>();

            @Override 
            public Void visitClass​(ClassTree node, Void nothing) {
                var shadowed = enclosingClass;
                enclosingClass = node;
                super.visitClass(node, null);
                enclosingClass = shadowed;
                return null;
            }

            @Override
            public Void visitMethod(MethodTree node, Void aVoid) {
                for (var ann : node.getModifiers().getAnnotations()) {
                    var type = ann.getAnnotationType();
                    if (type instanceof IdentifierTree) {
                        var id = (IdentifierTree) type;
                        var name = id.getName();
                        if (name.contentEquals("Test") || name.contentEquals("org.junit.Test")) {
                            found.add(new TestMethod(task, parse, enclosingClass, Optional.of(node)));
                            if (!testClasses.contains(enclosingClass)) {
                                found.add(new TestMethod(task, parse, enclosingClass, Optional.empty()));
                                testClasses.add(enclosingClass);
                            }
                        }
                    }
                }
                return super.visitMethod(node, aVoid);
            }

            List<TestMethod> run() {
                scan(parse, null);
                return found;
            }
        }

        return new Find().run();
    }
}
