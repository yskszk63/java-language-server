package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Test;

public class FindReferencesTest {
    private static final Logger LOG = Logger.getLogger("main");

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    protected List<String> items(String file, int row, int column) {
        var uri = FindResource.uri(file);
        var params = new ReferenceParams();

        params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
        params.setUri(uri.toString());
        params.setPosition(new Position(row - 1, column - 1));

        try {
            var locations = server.getTextDocumentService().references(params).get();
            var strings = new ArrayList<String>();
            for (var l : locations) {
                var fileName = Parser.fileName(URI.create(l.getUri()));
                var line = l.getRange().getStart().getLine();
                strings.add(String.format("%s(%d)", fileName, line + 1));
            }
            return strings;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void findAllReferences() {
        assertThat(items("/org/javacs/example/GotoOther.java", 6, 30), not(empty()));
    }

    @Test
    public void findConstructorReferences() {
        assertThat(items("/org/javacs/example/ConstructorRefs.java", 4, 10), contains("ConstructorRefs.java(9)"));
    }
}
