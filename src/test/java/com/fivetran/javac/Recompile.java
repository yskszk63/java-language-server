package com.fivetran.javac;

import com.sun.source.tree.ClassTree;
import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

public class Recompile extends Fixtures {
    @Test
    public void compileTwice() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetResourceFileObject file = new GetResourceFileObject("/CompileTwice.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        List<String> visits = new ArrayList<>();
        compiler.afterAnalyze(new BridgeExpressionScanner() {
            @Override
            protected void visitClass(ClassTree node) {
                super.visitClass(node);

                visits.add(node.getSimpleName().toString());
            }
        });
        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), empty());
        assertThat(visits, contains("CompileTwice"));

        // Compile again
        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), empty());
        assertThat(visits, contains("CompileTwice", "CompileTwice"));
    }
}
