package org.javacs;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePathScanner;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class RecompileTest {
    @Test
    public void compileTwice() {
        URI file = FindResource.uri("/org/javacs/example/CompileTwice.java");
        JavacHolder compiler = newCompiler();
        List<String> visits = new ArrayList<>();
        BatchResult compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        GetClass getClass = new GetClass(visits);

        compile.trees.forEach(tree -> getClass.scan(tree, null));

        assertThat(compile.errors.getDiagnostics(), empty());
        assertThat(visits, hasItems("CompileTwice", "NestedStaticClass", "NestedClass"));

        // Compile again
        compile = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        compile.trees.forEach(tree -> getClass.scan(tree, null));

        assertThat(compile.errors.getDiagnostics(), empty());
        assertThat(visits, hasItems("CompileTwice", "NestedStaticClass", "NestedClass",
                                    "CompileTwice", "NestedStaticClass", "NestedClass"));
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
        List<String> parsedClassNames = new ArrayList<>();
        GetClass getClass = new GetClass(parsedClassNames);

        goodCompile.trees.forEach(tree -> getClass.scan(tree, null));

        assertThat(goodErrors.getDiagnostics(), empty());
        assertThat(parsedClassNames, contains("FixParseErrorAfter"));
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
        List<String> parsedClassNames = new ArrayList<>();
        GetClass getClass = new GetClass(parsedClassNames);

        goodCompile.trees.forEach(tree -> getClass.scan(tree, null));

        assertThat(goodErrors.getDiagnostics(), empty());
        assertThat(parsedClassNames, contains("FixTypeErrorAfter"));
    }

    private static JavacHolder newCompiler() {
        return JavacHolder.createWithoutIndex(
                Collections.emptySet(),
                Collections.singleton(Paths.get("src/test/resources")),
                Paths.get("out")
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

    private static class GetClass extends TreePathScanner<Void, Void> {
        private final List<String> visits;

        public GetClass(List<String> visits) {
            this.visits = visits;
        }

        @Override
        public Void visitClass(ClassTree node, Void aVoid) {
            visits.add(node.getSimpleName().toString());

            return super.visitClass(node, aVoid);
        }
    }
}
