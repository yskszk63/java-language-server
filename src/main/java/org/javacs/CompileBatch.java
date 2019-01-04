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
import javax.lang.model.type.TypeKind;
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
        // Somehow, uri was not in batch
        var names = new StringJoiner(", ");
        for (var r : roots) {
            names.add(Parser.fileName(r.getSourceFile().toUri()));
        }
        throw new RuntimeException("File " + uri + " isn't in batch " + names);
    }

    public Optional<List<TreePath>> definitions(Element el) {
        LOG.info(String.format("Search for definitions of `%s` in %d files...", el, roots.size()));

        if (el.asType().getKind() == TypeKind.ERROR) {
            LOG.info(String.format("...`%s` is an error type, giving up", el.asType()));
            return Optional.empty();
        }

        var refs = new ArrayList<TreePath>();
        class FindDefinitions extends TreePathScanner<Void, Void> {
            boolean sameSymbol(Element found) {
                if (el.equals(found)) {
                    var uri = getCurrentPath().getCompilationUnit().getSourceFile().toUri();
                    var fileName = Parser.fileName(uri);
                    return true;
                }
                return false;
            }

            boolean isSubMethod(Element found) {
                if (!(el instanceof ExecutableElement)) return false;
                if (!(found instanceof ExecutableElement)) return false;
                var superMethod = (ExecutableElement) el;
                var subMethod = (ExecutableElement) found;
                var subType = (TypeElement) subMethod.getEnclosingElement();
                if (elements.overrides(subMethod, superMethod, subType)) {
                    LOG.info(String.format("...`%s.%s` overrides `%s`", subType, subMethod, superMethod));
                    return true;
                }
                return false;
            }

            void check(TreePath from) {
                var found = trees.getElement(from);
                var match = sameSymbol(found) || isSubMethod(found);
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
        return Optional.of(refs);
    }

    public Optional<List<TreePath>> references(Element to) {
        LOG.info(String.format("Search for references to `%s` in %d files...", to, roots.size()));

        if (to.asType().getKind() == TypeKind.ERROR) {
            LOG.info(String.format("...`%s` is an error type, giving up", to.asType()));
            return Optional.empty();
        }

        var refs = new ArrayList<TreePath>();
        class FindReferences extends TreePathScanner<Void, Void> {
            boolean sameSymbol(Element found) {
                if (to.equals(found)) {
                    var uri = getCurrentPath().getCompilationUnit().getSourceFile().toUri();
                    var fileName = Parser.fileName(uri);
                    return true;
                }
                return false;
            }

            boolean isSuperMethod(Element found) {
                if (!(to instanceof ExecutableElement)) return false;
                if (!(found instanceof ExecutableElement)) return false;
                var subMethod = (ExecutableElement) to;
                var subType = (TypeElement) subMethod.getEnclosingElement();
                var superMethod = (ExecutableElement) found;
                if (elements.overrides(subMethod, superMethod, subType)) {
                    LOG.info(String.format("...`%s.%s` overrides `%s`", subType, subMethod, superMethod));
                    return true;
                }
                return false;
            }

            void check(TreePath from) {
                var found = trees.getElement(from);
                var match = sameSymbol(found) || isSuperMethod(found);
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
        return Optional.of(refs);
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

    // TODO what if instead we just make references really fast?
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
                // TODO this is also a reference to a bunch of other things
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
