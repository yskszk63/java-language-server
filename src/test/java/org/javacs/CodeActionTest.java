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
    private static final String[][] cases = {
        {"org/javacs/action/TestRemoveVar.java", "Remove variable"},
        {"org/javacs/action/TestPrefixParam.java", "Prefix with underscore"},
    };

    @Test
    public void testCodeActions() {
        for (var c : cases) {
            var file = FindResource.path(c[0]);
            server.lint(List.of(file));
            var params = new CodeActionParams();
            params.textDocument = new TextDocumentIdentifier(file.toUri());
            params.context = new CodeActionContext();
            params.context.diagnostics = errors;
            var actions = server.codeAction(params);
            var titles = titles(actions);
            for (int i = 1; i < c.length; i++) {
                assertThat(titles, hasItem(c[i]));
            }
        }
    }

    private List<String> titles(List<CodeAction> actions) {
        var titles = new ArrayList<String>();
        for (var a : actions) {
            titles.add(a.title);
        }
        return titles;
    }
}
