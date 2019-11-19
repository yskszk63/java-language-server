package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

class CreateMissingMethod implements Rewrite {
    final String className, methodName;
    final String[] parameterNames;
    final JavaType[] parameterTypes;
    final JavaType returnType;

    CreateMissingMethod(
            String className,
            String methodName,
            String[] parameterNames,
            JavaType[] parameterTypes,
            JavaType returnType) {
        this.className = className;
        this.methodName = methodName;
        this.parameterNames = parameterNames;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        return Rewrite.CANCELLED;
    }
}
