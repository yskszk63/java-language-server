package org.javacs.rewrite;

import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.Map;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

public class AddSuppressWarningAnnotation implements Rewrite {
    final String className, methodName;
    final String[] erasedParameterTypes;

    public AddSuppressWarningAnnotation(String className, String methodName, String[] erasedParameterTypes) {
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
            var trees = Trees.instance(task.task);
            var path = trees.getPath(task.root(), method);
            if (path == null) {
                return CANCELLED;
            }
            var pos = trees.getSourcePositions();
            var startMethod = (int) pos.getStartPosition(task.root(), method);
            var lines = task.root().getLineMap();
            var line = (int) lines.getLineNumber(startMethod);
            var column = (int) lines.getColumnNumber(startMethod);
            var startLine = (int) lines.getStartPosition(line);
            var indent = " ".repeat(startMethod - startLine);
            var insertText = "@SuppressWarnings(\"unchecked\")\n" + indent;
            var insertPoint = new Position(line - 1, column - 1);
            var insert = new TextEdit(new Range(insertPoint, insertPoint), insertText);
            TextEdit[] edits = {insert};
            return Map.of(file, edits);
        }
    }
}
