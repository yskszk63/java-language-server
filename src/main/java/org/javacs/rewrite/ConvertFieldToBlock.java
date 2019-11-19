package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class ConvertFieldToBlock implements Rewrite {
    final Path file;
    final int position;

    ConvertFieldToBlock(Path file, int position) {
        this.file = file;
        this.position = position;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
