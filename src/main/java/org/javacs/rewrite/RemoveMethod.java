package org.javacs.rewrite;

import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.TextEdit;

public class RemoveMethod implements Rewrite {
    final String className, methodName;
    final String[] erasedParameterTypes;

    public RemoveMethod(String className, String methodName, String[] erasedParameterTypes) {
        this.className = className;
        this.methodName = methodName;
        this.erasedParameterTypes = erasedParameterTypes;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        var file = compiler.findTopLevelDeclaration(className);
        if (file == CompilerProvider.NOT_FOUND) {
            return CANCELLED;
        }
        try (var task = compiler.compile(file)) {
            var finder = new FindHelper(task);
            var method = finder.findMethod(task.root(), className, methodName, erasedParameterTypes);
            if (method == null) {
                return CANCELLED;
            }
            TextEdit[] edits = {new EditHelper(task.task).removeTree(task.root(), method)};
            return Map.of(file, edits);
        }
    }
}
