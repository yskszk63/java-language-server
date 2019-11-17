package org.javacs;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.javacs.lsp.*;

class Refactor {
    private final JavaCompilerService compiler;

    Refactor(JavaCompilerService compiler) {
        this.compiler = compiler;
    }

    CodeAction prependUnderscore(URI file, Range range) {
        var edit = new TextEdit(new Range(range.start, range.start), "_");
        var a = new CodeAction();
        a.kind = CodeActionKind.QuickFix;
        a.title = String.format("Prefix with underscore");
        a.edit = new WorkspaceEdit();
        a.edit.changes = Map.of(file, List.of(edit));
        return a;
    }
}
