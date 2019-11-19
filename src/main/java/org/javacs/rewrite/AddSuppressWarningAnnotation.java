package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class AddSuppressWarningAnnotation implements Rewrite {
    final String className, methodName;
    final String[] erasedParameterTypes;

    AddSuppressWarningAnnotation(String className, String methodName, String[] erasedParameterTypes) {
        this.className = className;
        this.methodName = methodName;
        this.erasedParameterTypes = erasedParameterTypes;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
