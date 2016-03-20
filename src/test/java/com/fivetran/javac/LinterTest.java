package com.fivetran.javac;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Type;
import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class LinterTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("");

    @Test
    public void compile() throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetResourceFileObject file = new GetResourceFileObject("/HelloWorld.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void inspectTree() throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        CollectMethods scanner = new CollectMethods();
        GetResourceFileObject file = new GetResourceFileObject("/HelloWorld.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");

        compiler.afterAnalyze(scanner);

        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(scanner.methodNames, hasItem("main"));
    }

    @Test
    public void missingMethodBody() throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        CollectMethods scanner = new CollectMethods();
        GetResourceFileObject file = new GetResourceFileObject("/MissingMethodBody.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");

        compiler.afterAnalyze(scanner);

        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(scanner.methodNames, hasItem("test"));
        assertThat(errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void incompleteAssignment() throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        CollectMethods parsed = new CollectMethods();
        CollectMethods compiled = new CollectMethods();
        GetResourceFileObject file = new GetResourceFileObject("/IncompleteAssignment.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");

        compiler.afterAnalyze(compiled);
        compiler.afterParse(parsed);

        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(parsed.methodNames, hasItem("test")); // Error recovery should have worked
        assertThat(compiled.methodNames, hasItem("test")); // Type error recovery should have worked
        assertThat(errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void undefinedSymbol() throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        CollectMethods scanner = new CollectMethods();
        GetResourceFileObject file = new GetResourceFileObject("/UndefinedSymbol.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");

        compiler.afterAnalyze(scanner);

        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(scanner.methodNames, hasItem("test")); // Type error, so parse tree is present

        Diagnostic<? extends JavaFileObject> d = errors.getDiagnostics().get(0);

        // Error position should span entire 'foo' symbol
        assertThat(d.getLineNumber(), greaterThan(0L));
        assertThat(d.getStartPosition(), greaterThan(0L));
        assertThat(d.getEndPosition(), greaterThan(d.getStartPosition() + 1));
        assertThat(errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void getType() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        MethodTypes scanner = new MethodTypes();
        GetResourceFileObject file = new GetResourceFileObject("/FooString.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");

        compiler.afterAnalyze(scanner);

        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), empty());
        assertThat(scanner.methodTypes, hasKey("test"));

        Type.MethodType type = scanner.methodTypes.get("test");

        assertThat(type.getReturnType().toString(), equalTo("java.lang.String"));
        assertThat(type.getParameterTypes(), hasSize(1));
        assertThat(type.getParameterTypes().get(0).toString(), equalTo("java.lang.String"));
    }

    @Test
    public void notJava() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetResourceFileObject file = new GetResourceFileObject("/NotJava.java.txt");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), not(empty()));
    }

    public static class MethodTypes extends BridgeExpressionScanner {
        public final Map<String, Type.MethodType> methodTypes = new HashMap<>();

        @Override
        protected void visitMethod(MethodTree node) {
            super.visitMethod(node);

            JavacTrees trees = JavacTrees.instance(super.context);
            Type.MethodType typeMirror = (Type.MethodType) trees.getTypeMirror(path());

            methodTypes.put(node.getName().toString(), typeMirror);
        }
    }

    public static class CollectMethods extends BridgeExpressionScanner {
        public final Set<String> methodNames = new HashSet<>();

        @Override
        protected void visitMethod(MethodTree node) {
            super.visitMethod(node);

            methodNames.add(node.getName().toString());
        }
    }
}
