package org.javacs.rewrite;

import java.nio.file.Path;

public class RewriteProvider {
    final CompilerProvider compiler;

    public RewriteProvider(CompilerProvider compiler) {
        this.compiler = compiler;
    }

    Rewrite renameVariable(Path file, int position, String newName) {
        return new RenameVariable(file, position, newName);
    }
}
