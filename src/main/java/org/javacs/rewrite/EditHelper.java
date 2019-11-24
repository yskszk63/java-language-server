package org.javacs.rewrite;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

class EditHelper {
    final JavacTask task;

    EditHelper(JavacTask task) {
        this.task = task;
    }

    TextEdit removeTree(CompilationUnitTree root, Tree remove) {
        var pos = Trees.instance(task).getSourcePositions();
        var lines = root.getLineMap();
        var start = pos.getStartPosition(root, remove);
        var end = pos.getEndPosition(root, remove);
        var startLine = (int) lines.getLineNumber(start);
        var startColumn = (int) lines.getColumnNumber(start);
        var startPos = new Position(startLine - 1, startColumn - 1);
        var endLine = (int) lines.getLineNumber(end);
        var endColumn = (int) lines.getColumnNumber(end);
        var endPos = new Position(endLine - 1, endColumn - 1);
        var range = new Range(startPos, endPos);
        return new TextEdit(range, "");
    }
}
