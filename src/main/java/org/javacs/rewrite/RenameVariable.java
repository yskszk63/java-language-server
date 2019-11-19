package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class RenameVariable implements Rewrite {
    final String className;
    final int position;
    final String newName;

    RenameVariable(String className, int position, String newName) {
        this.className = className;
        this.position = position;
        this.newName = newName;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return CANCELLED;
    }
}
