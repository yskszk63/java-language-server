package com.fivetran.javac;

import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

public class JavacHolderSpec extends Fixtures {
    @Test
    public void reference() {
        GetResourceFileObject file = new GetResourceFileObject("/ReferenceFrom.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        List<Diagnostic<? extends JavaFileObject>> errors = compiler.check(compiler.parse(file));

        assertThat(errors, empty());
    }

    @Test
    public void recompile() {
        GetResourceFileObject file = new GetResourceFileObject("/ReferenceFrom.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList("src/test/resources"),
                                               "out");
        List<Diagnostic<? extends JavaFileObject>> errors = compiler.check(compiler.parse(file));

        assertThat(errors, empty());
    }
}
