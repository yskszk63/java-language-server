package org.javacs;

import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class RecompileTest {
    @Test
    public void compileTwice() {
        URI file = FindResource.uri("/org/javacs/example/CompileTwice.java");
        JavacHolder compiler = newCompiler();
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), empty());

        // Compile again
        compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.errors.getDiagnostics(), empty());
    }

    @Test
    public void fixParseError() {
        URI bad = FindResource.uri("/org/javacs/example/FixParseErrorBefore.java");
        URI good = FindResource.uri("/org/javacs/example/FixParseErrorAfter.java");
        JavacHolder compiler = newCompiler();
        DiagnosticCollector<JavaFileObject> badErrors = compiler.compileBatch(Collections.singletonMap(bad, Optional.empty())).errors;

        assertThat(badErrors.getDiagnostics(), not(empty()));

        // Parse again
        BatchResult goodCompile = compiler.compileBatch(Collections.singletonMap(good, Optional.empty()));
        DiagnosticCollector<JavaFileObject> goodErrors = goodCompile.errors;

        assertThat(goodErrors.getDiagnostics(), empty());
    }

    @Test
    public void fixTypeError() {
        URI bad = FindResource.uri("/org/javacs/example/FixTypeErrorBefore.java");
        URI good = FindResource.uri("/org/javacs/example/FixTypeErrorAfter.java");
        JavacHolder compiler = newCompiler();
        DiagnosticCollector<JavaFileObject> badErrors = compiler.compileBatch(Collections.singletonMap(bad, Optional.empty())).errors;

        assertThat(badErrors.getDiagnostics(), not(empty()));

        // Parse again
        BatchResult goodCompile = compiler.compileBatch(Collections.singletonMap(good, Optional.empty()));
        DiagnosticCollector<JavaFileObject> goodErrors = goodCompile.errors;

        assertThat(goodErrors.getDiagnostics(), empty());
    }

    private static JavacHolder newCompiler() {
        return JavacHolder.create(
                Collections.singleton(Paths.get("src/test/test-project/workspace/src")),
                Collections.emptySet(),
                Paths.get("target/test-output")
        );
    }

    @Test
    public void keepTypeError() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/UndefinedSymbol.java");
        JavacHolder compiler = newCompiler();

        // Compile once
        DiagnosticCollector<JavaFileObject> errors = compiler.compileBatch(Collections.singletonMap(file, Optional.empty())).errors;
        assertThat(errors.getDiagnostics(), not(empty()));

        // Compile twice
        errors = compiler.compileBatch(Collections.singletonMap(file, Optional.empty())).errors;

        assertThat(errors.getDiagnostics(), not(empty()));
    }
}
