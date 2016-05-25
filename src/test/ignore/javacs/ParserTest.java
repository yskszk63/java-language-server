package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public class ParserTest extends Fixtures {
    @Test
    public void missingSemicolon() throws IOException, URISyntaxException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetResourceFileObject file = new GetResourceFileObject("/org/javacs/example/MissingSemicolon.java");
        JavacHolder compiler = new JavacHolder(Collections.emptyList(),
                                               Collections.singletonList(Paths.get("src/test/resources")),
                                               Paths.get("out"));
        List<String> methods = new ArrayList<>();

        compiler.afterParse(new TreeScanner() {
            @Override
            public void visitMethodDef(JCTree.JCMethodDecl node) {
                methods.add(node.getName().toString());
            }
        });

        compiler.onError(errors);
        compiler.parse(file);

        assertThat(methods, hasItem("methodWithMissingSemicolon"));
        assertThat(methods, hasItem("methodAfterMissingSemicolon"));
    }
}
