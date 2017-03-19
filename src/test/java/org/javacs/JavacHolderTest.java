package org.javacs;

import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class JavacHolderTest {
    @Test
    public void clear() throws NoSuchFieldException, IllegalAccessException {
        JavacHolder javac = new JavacHolder(Collections.emptySet(), Collections.singleton(Paths.get("src/test/resources")), Paths.get("target"), false);
        URI file = FindResource.uri("org/javacs/example/FooString.java");
        CompilationResult compile = javac.compile(Collections.singletonMap(file, Optional.empty()));
        Tree tree = ContextPrinter.tree(javac.context, 3);

        assertThat(tree.toString(), containsString("FooString"));

        javac.clear(file);

        tree = ContextPrinter.tree(javac.context, 3);

        System.out.println(tree.toString());

        assertThat(tree.toString(), not(containsString("FooString")));
    }
}
