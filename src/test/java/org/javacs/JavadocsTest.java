package org.javacs;

import com.sun.javadoc.RootDoc;
import com.sun.source.util.Trees;
import org.junit.Test;

import javax.lang.model.element.ExecutableElement;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class JavadocsTest {

    private static JavacHolder newCompiler() {
        return JavacHolder.createWithoutIndex(
                Collections.emptySet(),
                Collections.singleton(Paths.get("src/test/resources")),
                Paths.get("target/test-output")
        );
    }

    @Test
    public void findSystemDoc() throws IOException {
        Javadocs docs = new Javadocs(Collections.emptySet());

        docs.force("java.util.ArrayList");

        RootDoc root = docs.index("java.util.ArrayList");

        assertThat(root.classes(), not(emptyArray()));
    }

    @Test
    public void findMethodDoc() {
        Javadocs docs = new Javadocs(Collections.singleton(Paths.get("src/test/resources")));
        JavacHolder compiler = newCompiler();
        FocusedResult compile = compiler.compileFocused(
            Paths.get("src/test/resources/org/javacs/docs/TrickyDocstring.java").toUri(),
            Optional.empty(), 
            8, 16, 
            false
        );
        ExecutableElement method = (ExecutableElement) Trees.instance(compile.task).getElement(compile.cursor.get());

        docs.update(compile.compilationUnit.getSourceFile());

        assertTrue(docs.methodDoc(method).isPresent());
    }
}