package org.javacs.rewrite;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import org.javacs.LanguageServerFixture;
import org.junit.Test;

public class RewriteTest {
    final CompilerProvider compiler = LanguageServerFixture.getCompilerProvider();

    private Path file(String name) {
        return LanguageServerFixture.DEFAULT_WORKSPACE_ROOT
                .resolve("src/org/javacs/rewrite")
                .resolve(name)
                .toAbsolutePath();
    }

    @Test
    public void renameVariable() {
        var file = file("TestRenameVariable.java");
        var edits = new RenameVariable(file, 82, "bar").rewrite(compiler);
        assertThat(edits.keySet(), hasSize(1));
        assertThat(edits, hasKey(file));
    }

    @Test
    public void renameField() {
        var className = "org.javacs.rewrite.TestRenameField";
        var fieldName = "foo";
        var renamer = new RenameField(className, fieldName, "bar");
        var edits = renamer.rewrite(compiler);
        assertThat(edits.keySet(), hasSize(1));
        assertThat(edits, hasKey(file("TestRenameField.java")));
    }

    @Test
    public void renameMethod() {
        var className = "org.javacs.rewrite.TestRenameMethod";
        var methodName = "foo";
        String[] erasedParameterTypes = {};
        var renamer = new RenameMethod(className, methodName, erasedParameterTypes, "bar");
        var edits = renamer.rewrite(compiler);
        assertThat(edits.keySet(), hasSize(1));
        assertThat(edits, hasKey(file("TestRenameMethod.java")));
    }
}
