package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class RenameField implements Rewrite {
    final String className, fieldName, newName;

    RenameField(String className, String fieldName, String newName) {
        this.className = className;
        this.fieldName = fieldName;
        this.newName = newName;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
