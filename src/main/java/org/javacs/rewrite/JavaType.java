package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

/** JavaType represents a potentially parameterized named type. */
class JavaType implements Rewrite {
    final String name;
    final JavaType[] parameters;

    JavaType(String name, JavaType[] parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
