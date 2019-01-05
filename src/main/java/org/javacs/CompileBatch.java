package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
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
                // TODO need to check if class is compatible as well
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
        var map = references(List.of(to));
        if (map.size() > 1) {
            throw new RuntimeException(String.format("Searched for `%s` but found multiple %s", to, map.keySet()));
        }
        // Return the only element in the map
        for (var path : map.values()) {
            return Optional.of(path);
        }
        // Map is empty, to must have been removed due to errors
        return Optional.empty();
    }

    public Map<Element, List<TreePath>> references(List<Element> toAny) {
        LOG.info(String.format("Search for references to %d elements in %d files...", toAny.size(), roots.size()));

        var els = new ArrayList<Element>();
        for (var to : toAny) {
            if (to.asType().getKind() == TypeKind.ERROR) {
                LOG.info(String.format("...`%s` is an error type, giving up", to.asType()));
                continue;
            }
            els.add(to);
        }

        var refs = new HashMap<Element, List<TreePath>>();
        class FindReferences extends TreePathScanner<Void, Void> {
            boolean sameSymbol(Element from, Element to) {
                if (to.equals(from)) {
                    var uri = getCurrentPath().getCompilationUnit().getSourceFile().toUri();
                    var fileName = Parser.fileName(uri);
                    return true;
                }
                return false;
            }

            boolean isSuperMethod(Element from, Element to) {
                if (!(to instanceof ExecutableElement)) return false;
                if (!(from instanceof ExecutableElement)) return false;
                var subMethod = (ExecutableElement) to;
                var subType = (TypeElement) subMethod.getEnclosingElement();
                var superMethod = (ExecutableElement) from;
                // TODO need to check if class is compatible as well
                if (elements.overrides(subMethod, superMethod, subType)) {
                    LOG.info(String.format("...`%s.%s` overrides `%s`", subType, subMethod, superMethod));
                    return true;
                }
                return false;
            }

            void check(TreePath from) {
                for (var to : els) {
                    var fromEl = trees.getElement(from);
                    var match = sameSymbol(fromEl, to) || isSuperMethod(fromEl, to);
                    if (match) {
                        var list = refs.computeIfAbsent(to, __ -> new ArrayList<>());
                        list.add(from);
                    }
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
        var finder = new FindReferences();
        for (var r : roots) {
            finder.scan(r, null);
        }
        return refs;
    }

    /**
     * Find all elements in `file` that get turned into code-lenses. This needs to match the result of
     * `ParseFile#declarations`
     */
    public List<Element> declarations(URI file) {
        for (var r : roots) {
            if (!r.getSourceFile().toUri().equals(file)) continue;
            var paths = ParseFile.declarations(r);
            var els = new ArrayList<Element>();
            for (var p : paths) {
                var e = trees.getElement(p);
                assert e != null;
                els.add(e);
            }
            return els;
        }

        // Couldn't find file! Throw an error.
        var message = new StringJoiner(", ");
        for (var r : roots) {
            message.add(Parser.fileName(r.getSourceFile().toUri()));
        }
        throw new RuntimeException(file + " is not in " + message);
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

    private static boolean isField(Element to) {
        if (!(to instanceof VariableElement)) return false;
        var field = (VariableElement) to;
        return field.getEnclosingElement() instanceof TypeElement;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
