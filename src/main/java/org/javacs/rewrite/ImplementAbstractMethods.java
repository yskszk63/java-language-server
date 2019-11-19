package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class ImplementAbstractMethods implements Rewrite {
    final String className;

    ImplementAbstractMethods(String className) {
        this.className = className;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
