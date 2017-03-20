package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public class ParserTest {
    @Test
    public void missingSemicolon() throws IOException, URISyntaxException {
        JavacHolder compiler = newCompiler();
        List<String> methods = new ArrayList<>();
        URI file = FindResource.uri("/org/javacs/example/MissingSemicolon.java");

        compiler.compile(Collections.singletonMap(file, Optional.empty())).trees.forEach(t -> t.accept(new TreeScanner() {
            @Override
            public void visitMethodDef(JCTree.JCMethodDecl node) {
                methods.add(node.getName().toString());
            }
        }));

        assertThat(methods, hasItem("methodWithMissingSemicolon"));
        assertThat(methods, hasItem("methodAfterMissingSemicolon"));
    }

    private JavacHolder newCompiler() {
        return JavacHolder.createWithoutIndex(
                Collections.emptySet(),
                Collections.singleton(Paths.get("src/test/resources")),
                Paths.get("out")
        );
    }
}
