package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import com.google.common.base.Joiner;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.javacs.lsp.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class SearchTest {
    private static final Logger LOG = Logger.getLogger("main");

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    @BeforeClass
    public static void openSource() throws IOException {
        var uri = FindResource.uri("/org/javacs/example/AutocompleteBetweenLines.java");
        var textContent = Joiner.on("\n").join(Files.readAllLines(Paths.get(uri)));
        var document = new TextDocumentItem();

        document.setUri(uri.toString());
        document.setText(textContent);

        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(document, null));
    }

    private static Set<String> searchWorkspace(String query, int limit) {
        try {
            return server.getWorkspaceService()
                    .symbol(new WorkspaceSymbolParams(query))
                    .get()
                    .stream()
                    .map(result -> result.getName())
                    .limit(limit)
                    .collect(Collectors.toSet());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> searchFile(URI uri) {
        try {
            return server.getTextDocumentService()
                    .documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(uri.toString())))
                    .get()
                    .stream()
                    .map(result -> result.getLeft().getName())
                    .collect(Collectors.toSet());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void all() {
        var all = searchWorkspace("", 100);

        assertThat(all, not(empty()));
    }

    @Test
    public void searchClasses() {
        var all = searchWorkspace("ABetweenLines", Integer.MAX_VALUE);

        assertThat(all, hasItem("AutocompleteBetweenLines"));
    }

    @Test
    public void searchMethods() {
        var all = searchWorkspace("mStatic", Integer.MAX_VALUE);

        assertThat(all, hasItem("methodStatic"));
    }

    @Test
    public void symbolsInFile() {
        var path = "/org/javacs/example/AutocompleteMemberFixed.java";
        var all = searchFile(FindResource.uri(path));

        assertThat(
                all,
                hasItems(
                        "methodStatic", "method",
                        "methodStaticPrivate", "methodPrivate"));

        assertThat(
                all,
                hasItems(
                        "fieldStatic", "field",
                        "fieldStaticPrivate", "fieldPrivate"));
    }

    @Test
    public void explicitConstructor() {
        var path = "/org/javacs/example/ReferenceConstructor.java";
        var all = searchFile(FindResource.uri(path));

        assertThat("includes explicit constructor", all, hasItem("ReferenceConstructor"));
    }
}
