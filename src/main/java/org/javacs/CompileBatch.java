package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.tools.*;
import org.javacs.lsp.Range;

public class CompileBatch {
    private final JavaCompilerService parent;
    private final ReportProgress progress;
    private final JavacTask task;
    private final Trees trees;
    private final Elements elements;
    private final Types types;
    private final List<CompilationUnitTree> roots;

    CompileBatch(JavaCompilerService parent, Collection<? extends JavaFileObject> files, ReportProgress progress) {
        this.parent = parent;
        this.progress = progress;
        this.task = batchTask(parent, files);
        this.trees = Trees.instance(task);
        this.elements = task.getElements();
        this.types = task.getTypes();
        this.roots = new ArrayList<CompilationUnitTree>();
        // Print timing information for optimization
        var profiler = new Profiler();
        task.addTaskListener(profiler);
        // Show progress message through the UI
        class CountFiles implements TaskListener {
            Set<URI> parse = new HashSet<>(), enter = new HashSet<>(), analyze = new HashSet<>();

            void inc(String message) {
                var n = parse.size() + enter.size() + analyze.size();
                var total = files.size() * 3;
                progress.progress(message, n, total);
            }

            @Override
            public void started(TaskEvent e) {
                var uri = e.getSourceFile().toUri();
                switch (e.getKind()) {
                    case PARSE:
                        if (parse.add(uri)) inc("Parse sources");
                        break;
                    case ENTER:
                        if (enter.add(uri)) inc("Enter symbols");
                        break;
                    case ANALYZE:
                        var name = Parser.fileName(uri);
                        if (analyze.add(uri)) inc("Analyze " + name);
                        break;
                }
            }
        }
        task.addTaskListener(new CountFiles());
        // Compile all roots
        try {
            for (var t : task.parse()) roots.add(t);
            // The results of task.analyze() are unreliable when errors are present
            // You can get at `Element` values using `Trees`
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        profiler.print();
    }

    static JavacTask batchTask(JavaCompilerService parent, Collection<? extends JavaFileObject> sources) {
        parent.diags.clear();
        return (JavacTask)
                parent.compiler.getTask(
                        null,
                        parent.fileManager,
                        parent.diags::add,
                        JavaCompilerService.options(parent.sourcePath, parent.classPath),
                        Collections.emptyList(),
                        sources);
    }

    public Optional<Element> element(URI uri, int line, int character) {
        for (var root : roots) {
            if (root.getSourceFile().toUri().equals(uri)) {
                var path = CompileFocus.findPath(task, root, line, character);
                var el = trees.getElement(path);
                return Optional.ofNullable(el);
            }
        }
        throw new RuntimeException("File " + uri + " isn't in batch " + roots);
    }

    // TODO error is interpreted as object, which leads to "go-to-everywhere"
    public List<TreePath> definitions(Element el) {
        LOG.info(String.format("Search for definitions of `%s` in %d files...", el, roots.size()));

        var refs = new ArrayList<TreePath>();
        class FindDefinitions extends TreePathScanner<Void, Void> {
            boolean sameSymbol(Element found) {
                return el.equals(found);
            }

            boolean isSubMethod(Element found) {
                if (!(el instanceof ExecutableElement)) return false;
                if (!(found instanceof ExecutableElement)) return false;
                var superMethod = (ExecutableElement) el;
                var subMethod = (ExecutableElement) found;
                var subType = (TypeElement) subMethod.getEnclosingElement();
                return elements.overrides(subMethod, superMethod, subType);
            }

            boolean isSubType(Element found) {
                if (!(el instanceof TypeElement)) return false;
                if (!(found instanceof TypeElement)) return false;
                var superType = (TypeElement) el;
                var subType = (TypeElement) found;
                return types.isSubtype(subType.asType(), superType.asType());
            }

            void check(TreePath from) {
                var found = trees.getElement(from);
                var match = sameSymbol(found) || isSubMethod(found) || isSubType(found);
                if (match) refs.add(from);
            }

            @Override
            public Void visitClass(ClassTree t, Void __) {
                check(getCurrentPath());
                return super.visitClass(t, null);
            }

            @Override
            public Void visitMethod(MethodTree t, Void __) {
                check(getCurrentPath());
                return super.visitMethod(t, null);
            }

            @Override
            public Void visitVariable(VariableTree t, Void __) {
                check(getCurrentPath());
                return super.visitVariable(t, null);
            }
        }
        var finder = new FindDefinitions();
        for (var r : roots) {
            finder.scan(r, null);
        }
        return refs;
    }

    public List<TreePath> references(Element to) {
        LOG.info(String.format("Search for references to `%s` in %d files...", to, roots.size()));

        var refs = new ArrayList<TreePath>();
        class FindReferences extends TreePathScanner<Void, Void> {
            boolean sameSymbol(Element found) {
                return to.equals(found);
            }

            boolean isSuperMethod(Element found) {
                if (!(to instanceof ExecutableElement)) return false;
                if (!(found instanceof ExecutableElement)) return false;
                var subMethod = (ExecutableElement) to;
                var subType = (TypeElement) subMethod.getEnclosingElement();
                var superMethod = (ExecutableElement) found;
                return elements.overrides(subMethod, superMethod, subType);
            }

            boolean isSuperType(Element found) {
                if (!(to instanceof TypeElement)) return false;
                if (!(found instanceof TypeElement)) return false;
                var subType = (TypeElement) to;
                var superType = (TypeElement) found;
                return types.isSubtype(subType.asType(), superType.asType());
            }

            void check(TreePath from) {
                var found = trees.getElement(from);
                var match = sameSymbol(found) || isSuperMethod(found) || isSuperType(found);
                if (match) refs.add(from);
            }

            @Override
            public Void visitMemberReference(MemberReferenceTree t, Void __) {
                check(getCurrentPath());
                return super.visitMemberReference(t, null);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree t, Void __) {
                check(getCurrentPath());
                return super.visitMemberSelect(t, null);
            }

            @Override
            public Void visitIdentifier(IdentifierTree t, Void __) {
                check(getCurrentPath());
                return super.visitIdentifier(t, null);
            }

            @Override
            public Void visitNewClass(NewClassTree t, Void __) {
                check(getCurrentPath());
                return super.visitNewClass(t, null);
            }
        }
        var finder = new FindReferences();
        for (var r : roots) {
            finder.scan(r, null);
        }
        return refs;
    }

    public Map<URI, Index> countReferences() {
        var index = new HashMap<URI, Index>();
        for (var f : roots) {
            var uri = f.getSourceFile().toUri();
            var path = Paths.get(uri);
            var refs = index(trees, f);
            // Remember when file was modified
            FileTime modified;
            try {
                modified = Files.getLastModifiedTime(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Remember if file contains any errors
            var containsError = false;
            for (var d : parent.diags) {
                var isError = d.getKind() == Diagnostic.Kind.ERROR;
                var sameUri = d.getSource().toUri().equals(uri);
                if (isError && sameUri) containsError = true;
            }
            // Add file to index
            var i = new Index(refs, modified.toInstant(), containsError);
            index.put(uri, i);
        }
        return index;
    }

    public Optional<Range> range(TreePath path) {
        var uri = path.getCompilationUnit().getSourceFile().toUri();
        var file = Paths.get(uri);
        String contents;
        try {
            contents = Files.readAllLines(file).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ParseFile.range(task, contents, path);
    }

    private boolean toStringEquals(Object left, Object right) {
        return Objects.equals(Objects.toString(left, ""), Objects.toString(right, ""));
    }

    private static boolean isField(Element to) {
        if (!(to instanceof VariableElement)) return false;
        var field = (VariableElement) to;
        return field.getEnclosingElement() instanceof TypeElement;
    }

    static List<Ptr> index(Trees trees, CompilationUnitTree root) {
        var refs = new ArrayList<Ptr>();
        class IndexFile extends TreePathScanner<Void, Void> {
            Optional<Element> ref(TreePath from) {
                var root = from.getCompilationUnit();
                var lines = root.getLineMap();
                var to = trees.getElement(from);
                // Skip elements we can't find
                if (to == null) {
                    // LOG.warning(String.format("No element for `%s`", from.getLeaf()));
                    return Optional.empty();
                }
                // Skip non-methods
                if (!(to instanceof ExecutableElement || to instanceof TypeElement || isField(to))) {
                    return Optional.empty();
                }
                // Skip classes that are nested inside of methods
                if (!Ptr.canPoint(to)) {
                    return Optional.empty();
                }
                // TODO skip anything not on source path
                return Optional.of(to);
            }

            void check(TreePath from) {
                var r = ref(from);
                if (r.isPresent()) {
                    var ptr = new Ptr(r.get());
                    refs.add(ptr);
                }
            }

            @Override
            public Void visitMemberReference(MemberReferenceTree t, Void __) {
                check(getCurrentPath());
                return super.visitMemberReference(t, null);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree t, Void __) {
                check(getCurrentPath());
                return super.visitMemberSelect(t, null);
            }

            @Override
            public Void visitIdentifier(IdentifierTree t, Void __) {
                check(getCurrentPath());
                return super.visitIdentifier(t, null);
            }

            @Override
            public Void visitNewClass(NewClassTree t, Void __) {
                check(getCurrentPath());
                return super.visitNewClass(t, null);
            }
        }
        new IndexFile().scan(root, null);
        if (refs.size() > 0) {
            var n = refs.size();
            var file = Parser.fileName(root.getSourceFile().toUri());
            LOG.info(String.format("Found %d refs in %s", n, file));
        }
        return refs;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
