package org.javacs;

import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.source.util.Trees;
import org.junit.Ignore;
import org.junit.Test;

import javax.lang.model.element.ExecutableElement;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class JavadocsTest {

    private final Javadocs docs = new Javadocs(Collections.singleton(Paths.get("src/test/resources")));
    private final JavacHolder compiler = newCompiler();

    private static JavacHolder newCompiler() {
        return JavacHolder.createWithoutIndex(
                Collections.emptySet(),
                Collections.singleton(Paths.get("src/test/resources")),
                Paths.get("target/test-output")
        );
    }

    @Test
    public void findSystemDoc() throws IOException {
        RootDoc root = docs.index("java.util.ArrayList");

        assertThat(root.classes(), not(emptyArray()));
    }

    @Test
    public void findMethodDoc() {
        assertTrue("Found method", docs.methodDoc("org.javacs.docs.TrickyDocstring#example(java.lang.String,java.lang.String[],java.util.List)").isPresent());
    }

    @Test
    public void findParameterizedDoc() {
        assertTrue("Found method", docs.methodDoc("org.javacs.docs.TrickyDocstring#parameterized(java.lang.Object)").isPresent());
    }

    @Test 
    public void findInheritedDoc() {
        Optional<MethodDoc> found = docs.methodDoc("org.javacs.docs.SubDoc#method()");

        assertTrue("Found method", found.isPresent());

        Optional<String> docstring = found.flatMap(Javadocs::commentText);

        assertTrue("Has inherited doc", docstring.isPresent());
        assertThat("Inherited doc is not empty", docstring.get(), not(isEmptyOrNullString()));
    }

    @Test
    @Ignore // Doesn't work yet
    public void findInterfaceDoc() {
        Optional<MethodDoc> found = docs.methodDoc("org.javacs.docs.SubDoc#interfaceMethod()");

        assertTrue("Found method", found.isPresent());

        Optional<String> docstring = found.flatMap(Javadocs::commentText);

        assertTrue("Has inherited doc", docstring.isPresent());
        assertThat("Inherited doc is not empty", docstring.get(), not(isEmptyOrNullString()));
    }
}