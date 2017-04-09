package org.javacs;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.junit.Test;

import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class LinterTest {
    private static final Logger LOG = Logger.getLogger("main");

    private static final JavacHolder compiler = JavacHolder.createWithoutIndex(
            Collections.emptySet(),
            Collections.singleton(Paths.get("src/test/resources")),
            Paths.get("out")
    );

    @Test
    public void compile() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/HelloWorld.java");
        DiagnosticCollector<JavaFileObject> errors = compiler.compileBatch(Collections.singletonMap(file, Optional.empty())).errors;

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void inspectTree() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/HelloWorld.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        CollectMethods scanner = new CollectMethods();

        compile.trees.forEach(tree -> scanner.scan(tree, null));

        assertThat(scanner.methodNames, hasItem("main"));
    }

    @Test
    public void missingMethodBody() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/MissingMethodBody.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        CollectMethods scanner = new CollectMethods();

        compile.trees.forEach(tree -> scanner.scan(tree, null));

        assertThat(scanner.methodNames, hasItem("test"));
        assertThat(compile.errors.getDiagnostics(), not(empty()));

        // Lint again
        compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void incompleteAssignment() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/IncompleteAssignment.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        CollectMethods scanner = new CollectMethods();

        compile.trees.forEach(tree -> scanner.scan(tree, null));

        assertThat(scanner.methodNames, hasItem("test")); // Type error recovery should have worked
        assertThat(compile.errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void undefinedSymbol() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/UndefinedSymbol.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        CollectMethods scanner = new CollectMethods();

        compile.trees.forEach(tree -> scanner.scan(tree, null));

        assertThat(scanner.methodNames, hasItem("test")); // Type error, so parse tree is present

        assertThat(compile.errors.getDiagnostics(), not(empty()));

        Diagnostic<? extends JavaFileObject> d = compile.errors.getDiagnostics().get(0);

        // Error position should span entire 'foo' symbol
        assertThat(d.getLineNumber(), greaterThan(0L));
        assertThat(d.getStartPosition(), greaterThan(0L));
        assertThat(d.getEndPosition(), greaterThan(d.getStartPosition() + 1));
    }

    @Test
    public void getType() {
        URI file = FindResource.uri("/org/javacs/example/FooString.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        MethodTypes scanner = new MethodTypes(compile.task);

        compile.trees.forEach(tree -> scanner.scan(tree, null));

        assertThat(compile.errors.getDiagnostics(), empty());
        assertThat(scanner.methodTypes, hasKey("test"));

        ExecutableType type = scanner.methodTypes.get("test");

        assertThat(type.getReturnType().toString(), equalTo("java.lang.String"));
        assertThat(type.getParameterTypes(), hasSize(1));
        assertThat(type.getParameterTypes().get(0).toString(), equalTo("java.lang.String"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void notJava() {
        URI file = FindResource.uri("/org/javacs/example/NotJava.java.txt");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
    }

    @Test
    public void errorInDependency() {
        URI file = FindResource.uri("/org/javacs/example/ErrorInDependency.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), not(empty()));
    }
    
    @Test
    public void deprecationWarning() {
        URI file = FindResource.uri("/org/javacs/example/DeprecationWarning.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void parseError() {
        URI file = URI.create("/org/javacs/example/ArrowTry.java");
        String source =
                "package org.javacs.example;\n" +
                "\n" +
                "class Example {\n" +
                "    private static <In, Out> Function<In, Stream<Out>> catchClasspathErrors(Function<In, Stream<Out>> f) {\n" +
                "        return in -> try {\n" +
                "            return f.apply(in);\n" +
                "        } catch (Symbol.CompletionFailure failed) {\n" +
                "            LOG.warning(failed.getMessage());\n" +
                "            return Stream.empty();\n" +
                "        };\n" +
                "    }\n" +
                "}";
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.of(source)));

        assertThat(compile.errors.getDiagnostics(), not(empty()));

    }

    public static class MethodTypes extends TreePathScanner<Void, Void> {
        public final Map<String, ExecutableType> methodTypes = new HashMap<>();

        private final Trees trees;

        public MethodTypes(JavacTask task) {
            trees = Trees.instance(task);
        }

        @Override
        public Void visitMethod(MethodTree node, Void aVoid) {
            methodTypes.put(node.getName().toString(), (ExecutableType) trees.getTypeMirror(getCurrentPath()));

            return super.visitMethod(node, aVoid);
        }
    }

    public static class CollectMethods extends TreePathScanner<Void, Void> {
        public final Set<String> methodNames = new HashSet<>();

        @Override
        public Void visitMethod(MethodTree node, Void aVoid) {
            methodNames.add(node.getName().toString());

            return super.visitMethod(node, aVoid);
        }
    }

}
