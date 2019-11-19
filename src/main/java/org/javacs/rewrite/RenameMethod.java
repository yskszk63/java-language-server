package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class RenameMethod implements Rewrite {
    final String className, methodName;
    final String[] erasedParameterTypes;
    final String newName;

    RenameMethod(String className, String methodName, String[] erasedParameterTypes, String newName) {
        this.className = className;
        this.methodName = methodName;
        this.erasedParameterTypes = erasedParameterTypes;
        this.newName = newName;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
