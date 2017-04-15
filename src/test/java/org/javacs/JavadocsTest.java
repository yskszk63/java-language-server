package org.javacs;

import com.google.common.collect.ImmutableList;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import com.sun.tools.javadoc.api.JavadocTool;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import javax.lang.model.element.ExecutableElement;
import javax.tools.*;
import javax.tools.DocumentationTool.DocumentationTask;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

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
        RootDoc root = docs.index("java.util.ArrayList");

        assertThat(root.classes(), not(emptyArray()));

        Optional<MethodDoc> method = docs.methodDoc("java.util.ArrayList#add(java.lang.Object)");

        assertTrue("add is found", method.isPresent());
    }

    @Test
    public void methodKey() {
        Javadocs docs = new Javadocs(Collections.singleton(Paths.get("src/test/resources")));
        JavacHolder compiler = newCompiler();
        FocusedResult compile = compiler.compileFocused(
            Paths.get("src/test/resources/org/javacs/docs/TrickyDocstring.java").toUri(),
            Optional.empty(), 
            8, 16, 
            false
        );
        ExecutableElement method = (ExecutableElement) Trees.instance(compile.task).getElement(compile.cursor.get());

        assertThat(docs.methodKey(method), equalTo("org.javacs.docs.TrickyDocstring#example(java.lang.String,java.lang.String[],java.util.List)"));
    }
}