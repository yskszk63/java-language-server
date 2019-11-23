package org.javacs.rewrite;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import org.javacs.lsp.TextEdit;

class RenameVariable implements Rewrite {
    final Path file;
    final int position;
    final String newName;

    RenameVariable(Path file, int position, String newName) {
        this.file = file;
        this.position = position;
        this.newName = newName;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        try (var compile = compiler.compile(file)) {
            var trees = Trees.instance(compile.task);
            var pos = trees.getSourcePositions();
            var root = compile.root();
            Predicate<VariableTree> test =
                    t -> {
                        var candidate = pos.getStartPosition(root, t);
                        return candidate == position;
                    };
            var found = new FindVariable().scan(root, test);
            if (found == null) {
                return CANCELLED;
            }
            var rename = trees.getPath(root, found);
            var edits = new RenameHelper(compile.task).renameVariable(root, rename, newName);
            return Map.of(file, edits);
        }
    }
}
