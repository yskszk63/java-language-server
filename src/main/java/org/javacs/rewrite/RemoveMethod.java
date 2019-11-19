package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class RemoveMethod implements Rewrite {
    final String className, methodName;
    final String[] erasedParameterTypes;

    RemoveMethod(String className, String methodName, String[] erasedParameterTypes) {
        this.className = className;
        this.methodName = methodName;
        this.erasedParameterTypes = erasedParameterTypes;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
