package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import org.javacs.lsp.TextEdit;

public class RemoveClass implements Rewrite {
    final Path file;
    final int position;

    public RemoveClass(Path file, int position) {
        this.file = file;
        this.position = position;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        var task = compiler.parse(file);
        var type = findClass(task.task, task.root);
        if (type == null) {
            return CANCELLED;
        }
        TextEdit[] edits = {new EditHelper(task.task).removeTree(task.root, type)};
        return Map.of(file, edits);
    }

    private ClassTree findClass(JavacTask task, CompilationUnitTree root) {
        var trees = Trees.instance(task);
        var pos = trees.getSourcePositions();
        Predicate<ClassTree> test = t -> pos.getStartPosition(root, t) == position;
        return new FindClassDeclaration().scan(root, test);
    }
}
