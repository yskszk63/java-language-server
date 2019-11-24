package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Before;
import org.junit.Test;

public class CodeActionTest {
    private static List<Diagnostic> errors = new ArrayList<>();
    protected static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer(errors::add);

    @Before
    public void clearErrors() {
        errors.clear();
    }

    @Test
    public void testCantConvertToStatement() {
        assertThat(titles("org/javacs/action/TestCantConvertToStatement.java"), empty());
    }

    @Test
    public void testConvertToStatement() {
        assertThat(titles("org/javacs/action/TestConvertToStatement.java"), contains("Convert to statement"));
    }

    @Test
    public void testConvertToBlock() {
        assertThat(titles("org/javacs/action/TestConvertToBlock.java"), contains("Convert to block"));
    }

    @Test
    public void testRemoveDeclaration() {
        assertThat(titles("org/javacs/action/TestRemoveDeclaration.java"), contains("Remove method"));
    }

    @Test
    public void testUnusedException() {
        assertThat(titles("org/javacs/action/TestUnusedException.java"), empty());
    }

    @Test
    public void testSuppressWarning() {
        assertThat(titles("org/javacs/action/TestSuppressWarning.java"), contains("Suppress 'unchecked' warning"));
    }

    @Test
    public void testAddThrows() {
        assertThat(titles("org/javacs/action/TestAddThrows.java"), contains("Add 'throws'"));
    }

    @Test
    public void testAddImport() {
        String[] expect = {
            "Import 'java.util.List'", "Import 'com.google.gson.Gson'", "Import 'com.sun.source.util.TreePathScanner'"
        };
        assertThat(titles("org/javacs/action/TestAddImport.java"), hasItems(expect));
    }

    @Test
    public void testRemoveNotThrown() {
        assertThat(titles("org/javacs/action/TestRemoveNotThrown.java"), contains("Remove 'IOException'"));
    }

    @Test
    public void testGenerateConstructor() {
        assertThat(titles("org/javacs/action/TestGenerateConstructor.java"), contains("Generate constructor"));
    }

    @Test
    public void testDontGenerateConstructor() {
        assertThat(titles("org/javacs/action/TestDontGenerateConstructor.java"), not(hasItem("Generate constructor")));
    }

    private List<String> titles(String testFile, String... expect) {
        var file = FindResource.path(testFile);
        server.lint(List.of(file));
        var params = new CodeActionParams();
        params.textDocument = new TextDocumentIdentifier(file.toUri());
        params.context = new CodeActionContext();
        params.context.diagnostics = errors;
        var actions = server.codeAction(params);
        return extractTitles(actions);
    }

    private List<String> extractTitles(List<CodeAction> actions) {
        var titles = new ArrayList<String>();
        for (var a : actions) {
            titles.add(a.title);
        }
        return titles;
    }
}
