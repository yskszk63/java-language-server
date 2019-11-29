package org.javacs.navigation;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import org.javacs.CompileTask;
import org.javacs.lsp.Location;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;

class NavigationHelper {

    static Element findElement(CompileTask task, Path file, int line, int column) {
        for (var root : task.roots) {
            if (root.getSourceFile().toUri().equals(file.toUri())) {
                var trees = Trees.instance(task.task);
                var cursor = (int) root.getLineMap().getPosition(line, column);
                var tree = new FindElementAt(task.task).scan(root, cursor);
                if (tree == null) return null;
                var path = trees.getPath(root, tree);
                return trees.getElement(path);
            }
        }
        throw new RuntimeException("file not found");
    }

    static boolean isLocal(Element element) {
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            return true;
        }
        switch (element.getKind()) {
            case EXCEPTION_PARAMETER:
            case LOCAL_VARIABLE:
            case PARAMETER:
            case TYPE_PARAMETER:
                return true;
            default:
                return false;
        }
    }

    static boolean isMember(Element element) {
        switch (element.getKind()) {
            case ENUM_CONSTANT:
            case FIELD:
            case METHOD:
            case CONSTRUCTOR:
                return true;
            default:
                return false;
        }
    }

    static boolean isType(Element element) {
        switch (element.getKind()) {
            case ANNOTATION_TYPE:
            case CLASS:
            case ENUM:
            case INTERFACE:
                return true;
            default:
                return false;
        }
    }

    static Location location(CompileTask task, TreePath path) {
        return location(task, path, "");
    }

    static Location location(CompileTask task, TreePath path, CharSequence name) {
        var lines = path.getCompilationUnit().getLineMap();
        var pos = Trees.instance(task.task).getSourcePositions();
        var start = (int) pos.getStartPosition(path.getCompilationUnit(), path.getLeaf());
        var end = (int) pos.getEndPosition(path.getCompilationUnit(), path.getLeaf());
        if (name.length() > 0) {
            start = findNameIn(path.getCompilationUnit(), name, start, end);
            end = start + name.length();
        }
        var startLine = (int) lines.getLineNumber(start);
        var startColumn = (int) lines.getColumnNumber(start);
        var startPos = new Position(startLine - 1, startColumn - 1);
        var endLine = (int) lines.getLineNumber(end);
        var endColumn = (int) lines.getColumnNumber(end);
        var endPos = new Position(endLine - 1, endColumn - 1);
        var range = new Range(startPos, endPos);
        var uri = path.getCompilationUnit().getSourceFile().toUri();
        return new Location(uri, range);
    }

    static int findNameIn(CompilationUnitTree root, CharSequence name, int start, int end) {
        CharSequence contents;
        try {
            contents = root.getSourceFile().getCharContent(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var matcher = Pattern.compile("\\b" + name + "\\b").matcher(contents);
        matcher.region(start, end);
        if (matcher.find()) {
            return matcher.start();
        }
        return -1;
    }
}
