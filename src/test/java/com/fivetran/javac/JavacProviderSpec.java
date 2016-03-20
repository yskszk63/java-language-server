package com.fivetran.javac;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

public class JavacProviderSpec extends Fixtures {
    @Test
    public void parse() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        BridgeExpressionScanner doNothing = new BridgeExpressionScanner();
        Context context = JavacProvider.compiler(Collections.emptyList(),
                                                       Collections.emptyList(),
                                                       true,
                                                       "out",
                                                       errors,
                                                       doNothing,
                                                       doNothing,
                                                       doNothing);
        JavaCompiler compiler = JavaCompiler.instance(context);
        JCTree.JCCompilationUnit tree = compiler.parse(new GetResourceFileObject("/HelloWorld.java"));
        List<String> methods = new ArrayList<>();

        tree.accept(new TreeScanner() {
            @Override
            public void visitMethodDef(JCTree.JCMethodDecl that) {
                super.visitMethodDef(that);

                methods.add(that.getName().toString());
            }
        });

        assertThat(methods, contains("main"));
    }

    @Test
    public void enter() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        BridgeExpressionScanner doNothing = new BridgeExpressionScanner();
        Context context = JavacProvider.compiler(Collections.emptyList(),
                                                       Collections.emptyList(),
                                                       true,
                                                       "out",
                                                       errors,
                                                       doNothing,
                                                       doNothing,
                                                       doNothing);
        JavaCompiler compiler = JavaCompiler.instance(context);
        JCTree.JCCompilationUnit tree = compiler.parse(new GetResourceFileObject("/HelloWorld.java"));

        compiler.enterTrees(com.sun.tools.javac.util.List.of(tree));

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void reference() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        BridgeExpressionScanner doNothing = new BridgeExpressionScanner();
        Context context = JavacProvider.compiler(Collections.emptyList(),
                                                 Collections.singletonList("src/main/resources"),
                                                 true,
                                                 "out",
                                                       errors,
                                                       doNothing,
                                                       doNothing,
                                                       doNothing);
        JavaCompiler compiler = JavaCompiler.instance(context);
        JCTree.JCCompilationUnit tree = compiler.parse(new GetResourceFileObject("/ReferenceFrom.java"));

        compiler.enterTrees(com.sun.tools.javac.util.List.of(tree));

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void analyze() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        BridgeExpressionScanner doNothing = new BridgeExpressionScanner();
        BridgeExpressionScanner getType = new BridgeExpressionScanner() {
            @Override
            public void scan(Tree node) {
                super.scan(node);
            }
        };
        Context context = JavacProvider.compiler(Collections.emptyList(),
                                                 Collections.singletonList("src/test/resources"),
                                                 true,
                                                 "out",
                                                 errors,
                                                 doNothing,
                                                 doNothing,
                                                 getType);
        JavaCompiler compiler = JavaCompiler.instance(context);
        GetResourceFileObject file = new GetResourceFileObject("/ReferenceFrom.java");
        JCTree.JCCompilationUnit tree = compiler.parse(file);

        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.of(tree)));

        assertThat(errors.getDiagnostics(), empty());

        Todo todo = Todo.instance(context);

        while (!todo.isEmpty())
            compiler.generate(compiler.desugar(compiler.flow(compiler.attribute(todo.remove()))));

        assertThat(errors.getDiagnostics(), empty());

        // Compile again

        compiler.processAnnotations(compiler.enterTrees(com.sun.tools.javac.util.List.of(tree)));

        assertThat(errors.getDiagnostics(), empty());

        todo = Todo.instance(context);

        while (!todo.isEmpty())
            compiler.generate(compiler.desugar(compiler.flow(compiler.attribute(todo.remove()))));

        assertThat(errors.getDiagnostics(), empty());
    }
}
