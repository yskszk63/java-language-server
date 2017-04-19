package org.javacs;

import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class LinterTest {
    private static final Logger LOG = Logger.getLogger("main");

    private static final JavacHolder compiler = JavacHolder.create(
            Collections.emptySet(),
            Collections.singleton(Paths.get("src/test/resources")),
            Paths.get("target/test-output")
    );

    @Test
    public void compile() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/HelloWorld.java");
        DiagnosticCollector<JavaFileObject> errors = compiler.compileBatch(Collections.singletonMap(file, Optional.empty())).errors;

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void missingMethodBody() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/MissingMethodBody.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void incompleteAssignment() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/IncompleteAssignment.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void undefinedSymbol() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/UndefinedSymbol.java");
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), not(empty()));

        Diagnostic<? extends JavaFileObject> d = compile.errors.getDiagnostics().get(0);

        // Error position should span entire 'foo' symbol
        assertThat(d.getLineNumber(), greaterThan(0L));
        assertThat(d.getStartPosition(), greaterThan(0L));
        assertThat(d.getEndPosition(), greaterThan(d.getStartPosition() + 1));
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

}
