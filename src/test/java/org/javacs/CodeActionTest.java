package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Test;

public class CodeActionTest {
    private static List<Diagnostic> errors = new ArrayList<>();
    protected static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer(errors::add);

    @Test
    public void testCantConvertToStatement() {
        check("org/javacs/action/TestCantConvertToStatement.java");
    }

    @Test
    public void testConvertToStatement() {
        check("org/javacs/action/TestConvertToStatement.java", "Convert to statement");
    }

    @Test
    public void testConvertToBlock() {
        check("org/javacs/action/TestConvertToBlock.java", "Convert to block");
    }

    @Test
    public void testRemoveDeclaration() {
        check("org/javacs/action/TestRemoveDeclaration.java", "Remove declaration");
    }

    @Test
    public void testUnusedException() {
        check("org/javacs/action/TestUnusedException.java");
    }

    public void check(String testFile, String... expect) {
        var file = FindResource.path(testFile);
        server.lint(List.of(file));
        var params = new CodeActionParams();
        params.textDocument = new TextDocumentIdentifier(file.toUri());
        params.context = new CodeActionContext();
        params.context.diagnostics = errors;
        var actions = server.codeAction(params);
        var titles = titles(actions);
        assertThat(titles, containsInAnyOrder(expect));
    }

    private List<String> titles(List<CodeAction> actions) {
        var titles = new ArrayList<String>();
        for (var a : actions) {
            titles.add(a.title);
        }
        return titles;
    }
}
