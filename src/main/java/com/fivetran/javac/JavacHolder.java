package com.fivetran.javac;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Options;

import javax.lang.model.element.TypeElement;
import javax.tools.*;
import java.util.*;
import java.util.logging.Logger;

public class JavacHolder {
    private static final Logger LOG = Logger.getLogger("");
    private final List<String> classPath, sourcePath;
    private final String outputDirectory;
    public final Context context = new Context();
    private DiagnosticListener<JavaFileObject> errorsDelegate = diagnostic -> {};
    private final DiagnosticListener<JavaFileObject> errors = diagnostic -> {
        errorsDelegate.report(diagnostic);
    };

    {
        context.put(DiagnosticListener.class, errors);
    }

    private final IncrementalLog log = new IncrementalLog(context);
    public final JavacFileManager fileManager = new JavacFileManager(context, true, null);
    private final Check check = Check.instance(context);
    private final FuzzyParserFactory parserFactory = FuzzyParserFactory.instance(context);
    private final Options options = Options.instance(context);
    private final JavaCompiler compiler = JavaCompiler.instance(context);
    private final Todo todo = Todo.instance(context);
    private final JavacTrees trees = JavacTrees.instance(context);
    private final Map<TaskEvent.Kind, List<TreeScanner>> beforeTask = new HashMap<>(), afterTask = new HashMap<>();

    public JavacHolder(List<String> classPath, List<String> sourcePath, String outputDirectory) {
        this.classPath = classPath;
        this.sourcePath = sourcePath;
        this.outputDirectory = outputDirectory;

        options.put("-classpath", Joiner.on(":").join(classPath));
        options.put("-sourcepath", Joiner.on(":").join(sourcePath));
        options.put("-d", outputDirectory);

        MultiTaskListener.instance(context).add(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                LOG.info("started " + e);

                List<TreeScanner> todo = beforeTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (TreeScanner visitor : todo) {
                    JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                    unit.accept(visitor);
                }
            }

            @Override
            public void finished(TaskEvent e) {
                LOG.info("finished " + e);

                List<TreeScanner> todo = afterTask.getOrDefault(e.getKind(), Collections.emptyList());

                for (TreeScanner visitor : todo) {
                    JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) e.getCompilationUnit();

                    unit.accept(visitor);
                }
            }
        });
    }

    public void afterParse(TreeScanner... scan) {
        afterTask.put(TaskEvent.Kind.PARSE, ImmutableList.copyOf(scan));
    }

    public void afterAnalyze(TreeScanner... scan) {
        afterTask.put(TaskEvent.Kind.ANALYZE, ImmutableList.copyOf(scan));
    }

    public void onError(DiagnosticListener<JavaFileObject> callback) {
        errorsDelegate = callback;
    }

    public JCTree.JCCompilationUnit parse(JavaFileObject source) {
        clear(source);

        JCTree.JCCompilationUnit result = compiler.parse(source);

        // Search for class definitions in this file
        // Remove them from downstream caches so that we can re-compile this class
        result.accept(new TreeScanner() {
            @Override
            public void visitClassDef(JCTree.JCClassDecl that) {
                super.visitClassDef(that);

                clear(that.name);
            }
        });

        return result;
    }

    public void compile(JCTree.JCCompilationUnit source) {
        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.of(source)));

        while (!todo.isEmpty())
            compiler.generate(compiler.desugar(compiler.flow(compiler.attribute(todo.remove()))));
    }

    /**
     * Remove source file from caches in the parse stage
     */
    private void clear(JavaFileObject source) {
        log.clear(source);
    }

    /**
     * Remove class definitions from caches in the compile stage
     */
    private void clear(Name name) {
        check.compiled.remove(name);
    }

    public static class SymbolLocation {
        public final JavaFileObject file;
        public final long startPosition, endPosition;

        public SymbolLocation(JavaFileObject file, long startPosition, long endPosition) {
            this.file = file;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
    public Optional<SymbolLocation> locate(Symbol symbol) {
        JavaFileObject file = symbol.enclClass().classfile;

        if (!file.toUri().getScheme().equals("file"))
            return Optional.empty();
        else if (symbol instanceof Symbol.VarSymbol) {
            int startPosition = ((Symbol.VarSymbol) symbol).pos;
            int endPosition = startPosition + symbol.name.length();

            return Optional.of(new SymbolLocation(file, startPosition, endPosition));
        }
        else if (symbol instanceof Symbol.ClassSymbol) {
            Symbol.ClassSymbol type = (Symbol.ClassSymbol) symbol;
            JCTree.JCClassDecl tree = trees.getTree(type);
            CompilationUnitTree unit = compilationUnit(symbol);
            DocSourcePositions pos = trees.getSourcePositions();
            long startPosition = pos.getStartPosition(unit, tree);
            long endPosition = pos.getEndPosition(unit, tree);

            return Optional.of(new SymbolLocation(file, startPosition, endPosition));
        }
        else if (symbol instanceof Symbol.MethodSymbol) {
            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
            JCTree.JCMethodDecl tree = trees.getTree(method);
            long startPosition = tree.pos;
            long endPosition = startPosition + symbol.name.length();

            return Optional.of(new SymbolLocation(file, startPosition, endPosition));
        }
        else {
            LOG.severe("Don't know what to do with " + symbol);

            return Optional.empty();
        }
    }

    private CompilationUnitTree compilationUnit(Symbol symbol) {
        return trees.getPath(symbol).getCompilationUnit();
    }
}
