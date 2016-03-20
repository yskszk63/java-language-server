package com.fivetran.javac;

import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

public class JavacHolderSpec extends Fixtures {
    @Test
    public void reference() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetResourceFileObject file = new GetResourceFileObject("/ReferenceFrom.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void recompile() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetResourceFileObject file = new GetResourceFileObject("/ReferenceFrom.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        compiler.onError(errors);
        compiler.check(compiler.parse(file));

        assertThat(errors.getDiagnostics(), empty());
    }
}
