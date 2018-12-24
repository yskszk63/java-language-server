package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Test;

public class CodeLensTest {

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private List<? extends CodeLens> lenses(String file) {
        var uri = FindResource.uri(file);
        var params = new CodeLensParams(new TextDocumentIdentifier(uri.toString()));
        try {
            return server.getTextDocumentService().codeLens(params).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> commands(List<? extends CodeLens> lenses) {
        var commands = new ArrayList<String>();
        for (var lens : lenses) {
            var command = new StringJoiner(", ");
            for (var arg : lens.getCommand().getArguments()) {
                if (arg instanceof Optional) arg = ((Optional) arg).orElse(null);
                command.add(Objects.toString(arg));
            }
            commands.add(command.toString());
        }
        return commands;
    }

    @Test
    public void codeLens() {
        var file = "/org/javacs/example/HasTest.java";
        var lenses = lenses(file);
        assertThat(lenses, not(empty()));

        var commands = commands(lenses);
        assertThat(commands, hasItem(containsString("HasTest, null")));
        assertThat(commands, hasItem(containsString("HasTest, testMethod")));
        assertThat(commands, hasItem(containsString("HasTest, otherTestMethod")));
    }
}
