package org.javacs.rewrite;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

class RenameHelper {
    final Trees trees;

    RenameHelper(JavacTask task) {
        this.trees = Trees.instance(task);
    }

    TextEdit[] rename(List<CompilationUnitTree> roots, TreePath rename, String newName) {
        var target = trees.getElement(rename);
        var found = findAll(roots, target);
        return replaceAll(found, newName);
    }

    private List<TreePath> findAll(List<CompilationUnitTree> roots, Element target) {
        var found = new ArrayList<TreePath>();
        Consumer<TreePath> forEach =
                path -> {
                    var candidate = trees.getElement(path);
                    if (target.equals(candidate)) {
                        found.add(path);
                    }
                };
        var scanAll = new FindReferences();
        for (var root : roots) {
            scanAll.scan(root, forEach);
        }
        return found;
    }

    private TextEdit[] replaceAll(List<TreePath> found, String newName) {
        var pos = trees.getSourcePositions();
        var edits = new TextEdit[found.size()];
        var i = 0;
        for (var f : found) {
            var root = f.getCompilationUnit();
            var lines = root.getLineMap();
            var startPos = pos.getStartPosition(root, f.getLeaf());
            var endPos = pos.getEndPosition(root, f.getLeaf());
            if (f.getLeaf() instanceof VariableTree) {
                var variable = (VariableTree) f.getLeaf();
                startPos = fixStartPosition(root, variable, startPos);
                endPos = startPos + variable.getName().length();
            }
            var startLine = (int) lines.getLineNumber(startPos);
            var startColumn = (int) lines.getColumnNumber(startPos);
            var endLine = (int) lines.getLineNumber(endPos);
            var endColumn = (int) lines.getColumnNumber(endPos);
            var range = new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
            edits[i++] = new TextEdit(range, newName);
        }
        return edits;
    }

    private long fixStartPosition(CompilationUnitTree root, VariableTree variable, long startPos) {
        try {
            var contents = root.getSourceFile().getCharContent(true);
            var matcher = Pattern.compile("\\b" + variable.getName() + "\\b").matcher(contents);
            if (matcher.find((int) startPos)) {
                return matcher.start();
            }
            return startPos;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
