package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;
import org.javacs.lsp.DocumentFormattingParams;
import org.javacs.lsp.TextDocumentIdentifier;
import org.javacs.lsp.TextEdit;
import org.junit.Test;

public class FormattingTest {

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private List<TextEdit> formatting(String file) {
        var uri = FindResource.uri(file);
        var params = new DocumentFormattingParams();
        params.textDocument = new TextDocumentIdentifier(uri);
        return server.formatting(params);
    }

    @Test
    public void addOverride() {
        var edits = formatting("/org/javacs/example/AddOverride.java");
        assertThat(edits, not(empty()));
    }
}
