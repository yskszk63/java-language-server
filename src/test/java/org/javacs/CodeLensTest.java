package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Test;

public class CodeLensTest {

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    @Test
    public void codeLens() {
        var file = "/org/javacs/example/HasTest.java";
        var uri = FindResource.uri(file);
        var params = new CodeLensParams(new TextDocumentIdentifier(uri.toString()));
        try {
            var lenses = server.getTextDocumentService().codeLens(params).get();
            assertThat(lenses, not(empty()));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
