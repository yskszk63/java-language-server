package org.javacs.rewrite;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

class RenameHelper {
    final Trees trees;
    final Types types;

    RenameHelper(JavacTask task) {
        this.trees = Trees.instance(task);
        this.types = task.getTypes();
    }

    TextEdit[] renameVariable(CompilationUnitTree root, TreePath rename, String newName) {
        var target = trees.getElement(rename);
        var found = findVariableReferences(root, target);
        return replaceAll(found, newName);
    }

    Map<Path, TextEdit[]> renameMethod(
            List<CompilationUnitTree> roots,
            String className,
            String methodName,
            String[] erasedParameterTypes,
            String newName) {
        var allEdits = new HashMap<Path, TextEdit[]>();
        for (var root : roots) {
            var file = Paths.get(root.getSourceFile().toUri());
            var references = findMethodReferences(root, className, methodName, erasedParameterTypes);
            if (references.isEmpty()) continue;
            var fileEdits = replaceAll(references, newName);
            allEdits.put(file, fileEdits);
        }
        return allEdits;
    }

    private List<TreePath> findVariableReferences(CompilationUnitTree root, Element target) {
        var found = new ArrayList<TreePath>();
        Consumer<TreePath> forEach =
                path -> {
                    var candidate = trees.getElement(path);
                    if (target.equals(candidate)) {
                        found.add(path);
                    }
                };
        new FindReferences().scan(root, forEach);
        return found;
    }

    private List<TreePath> findMethodReferences(
            CompilationUnitTree root, String className, String methodName, String[] erasedParameterTypes) {
        var found = new ArrayList<TreePath>();
        Consumer<TreePath> forEach =
                path -> {
                    if (isMethodReference(path, className, methodName, erasedParameterTypes)) {
                        found.add(path);
                    }
                };
        new FindMethodReferences().scan(root, forEach);
        return found;
    }

    private boolean isMethodReference(
            TreePath path, String className, String methodName, String[] erasedParameterTypes) {
        var candidate = trees.getElement(path);
        if (!(candidate instanceof ExecutableElement)) return false;
        var method = (ExecutableElement) candidate;
        var parent = (TypeElement) method.getEnclosingElement();
        if (!parent.getQualifiedName().contentEquals(className)) return false;
        if (!method.getSimpleName().contentEquals(methodName)) return false;
        if (method.getParameters().size() != erasedParameterTypes.length) return false;
        for (var i = 0; i < erasedParameterTypes.length; i++) {
            var erasure = types.erasure(method.getParameters().get(i).asType());
            var same = erasure.toString().equals(erasedParameterTypes[i]);
            if (!same) return false;
        }
        return true;
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
                startPos = findName(root, startPos, variable.getName());
                endPos = startPos + variable.getName().length();
            }
            if (f.getLeaf() instanceof MethodTree) {
                var method = (MethodTree) f.getLeaf();
                startPos = pos.getEndPosition(root, method.getReturnType());
                startPos = findName(root, startPos, method.getName());
                endPos = startPos + method.getName().length();
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

    private long findName(CompilationUnitTree root, long startPos, CharSequence name) {
        try {
            var contents = root.getSourceFile().getCharContent(true);
            var matcher = Pattern.compile("\\b" + name + "\\b").matcher(contents);
            if (matcher.find((int) startPos)) {
                return matcher.start();
            }
            return startPos;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
