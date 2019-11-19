package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class AddException implements Rewrite {
    final String className, methodName;
    final String[] erasedParameterTypes;
    final String exceptionType;

    AddException(String className, String methodName, String[] erasedParameterTypes, String exceptionType) {
        this.className = className;
        this.methodName = methodName;
        this.erasedParameterTypes = erasedParameterTypes;
        this.exceptionType = exceptionType;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
