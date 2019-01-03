package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Ignore;
import org.junit.Test;

// TODO change :n to (n)
public class GotoTest {
    private static final String file = "/org/javacs/example/Goto.java";
    private static final String defaultConstructorFile = "/org/javacs/example/GotoDefaultConstructor.java";

    @Test
    public void localVariable() {
        var suggestions = doGoto(file, 10, 9);

        assertThat(suggestions, hasItem("Goto.java:5"));
    }

    @Test
    public void defaultConstructor() {
        var suggestions = doGoto(defaultConstructorFile, 5, 46);

        assertThat(suggestions, hasItem("GotoDefaultConstructor.java:3"));
    }

    @Test
    public void constructor() {
        var suggestions = doGoto(file, 11, 21);

        assertThat(suggestions, hasItem("Goto.java:3"));
    }

    @Test
    public void className() {
        var suggestions = doGoto(file, 16, 9);

        assertThat(suggestions, hasItem("Goto.java:3"));
    }

    @Test
    public void staticField() {
        var suggestions = doGoto(file, 13, 22);

        assertThat(suggestions, hasItem("Goto.java:36"));
    }

    @Test
    public void field() {
        var suggestions = doGoto(file, 14, 22);

        assertThat(suggestions, hasItem("Goto.java:37"));
    }

    @Test
    public void staticMethod() {
        var suggestions = doGoto(file, 16, 14);

        assertThat(suggestions, hasItem("Goto.java:38"));
    }

    @Test
    public void method() {
        var suggestions = doGoto(file, 17, 14);

        assertThat(suggestions, hasItem("Goto.java:41"));
    }

    @Test
    public void staticMethodReference() {
        var suggestions = doGoto(file, 19, 27);

        assertThat(suggestions, hasItem("Goto.java:38"));
    }

    @Test
    public void methodReference() {
        var suggestions = doGoto(file, 20, 27);

        assertThat(suggestions, hasItem("Goto.java:41"));
    }

    @Test
    public void otherStaticMethod() {
        var suggestions = doGoto(file, 29, 25);

        assertThat(suggestions, hasItem(startsWith("GotoOther.java:")));
    }

    @Test
    public void otherMethod() {
        var suggestions = doGoto(file, 30, 18);

        assertThat(suggestions, hasItem(startsWith("GotoOther.java:")));
    }

    @Test
    public void otherCompiledFile() {
        var suggestions = doGoto(file, 29, 25);

        assertThat(suggestions, hasItem(startsWith("GotoOther.java:")));
    }

    @Test
    @Ignore // TODO
    public void typeParam() {
        var suggestions = doGoto(file, 46, 12);

        assertThat(suggestions, hasItem("Goto.java:3"));
    }

    @Test
    public void gotoEnum() {
        String file = "/org/javacs/example/GotoEnum.java";

        assertThat(doGoto(file, 6, 31), not(empty()));
        assertThat(doGoto(file, 6, 36), not(empty()));
    }

    @Test
    public void gotoOverload() {
        String file = "/org/javacs/example/GotoOverload.java";

        assertThat(doGoto(file, 7, 12), hasItem("GotoOverload.java:4"));
        assertThat(doGoto(file, 8, 12), hasItem("GotoOverload.java:12"));
        assertThat(doGoto(file, 9, 12), hasItem("GotoOverload.java:16"));
    }

    @Test
    public void gotoOverloadInOtherFile() {
        String file = "/org/javacs/example/GotoOverloadInOtherFile.java";

        assertThat(doGoto(file, 5, 25), hasItem("GotoOverload.java:4"));
        assertThat(doGoto(file, 6, 25), hasItem("GotoOverload.java:12"));
        assertThat(doGoto(file, 7, 25), hasItem("GotoOverload.java:16"));
    }

    @Test
    public void gotoImplementation() {
        String file = "/org/javacs/example/GotoImplementation.java";

        assertThat(doGoto(file, 5, 18), hasItems("GotoImplementation.java:9", "GotoImplementation.java:14"));
    }

    @Test
    public void gotoError() {
        String file = "/org/javacs/example/GotoError.java";

        assertThat(doGoto(file, 5, 22), empty());
    }

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private List<String> doGoto(String file, int row, int column) {
        TextDocumentIdentifier document = new TextDocumentIdentifier();

        document.uri = FindResource.uri(file);

        Position position = new Position();

        position.line = row - 1;
        position.character = column - 1;

        TextDocumentPositionParams p = new TextDocumentPositionParams();

        p.textDocument = document;
        p.position = position;

        var locations = server.gotoDefinition(p);
        var strings = new ArrayList<String>();
        for (var l : locations) {
            var fileName = Paths.get(l.uri).getFileName();
            var start = l.range.start;
            strings.add(String.format("%s:%d", fileName, start.line + 1));
        }
        return strings;
    }
}
