package org.javacs.markup;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.Paths;
import javax.lang.model.element.*;
import org.javacs.FileStore;

class Colorizer extends TreePathScanner<Void, SemanticColors> {
    private final Trees trees;

    Colorizer(JavacTask task) {
        trees = Trees.instance(task);
    }

    private void check(Name name, SemanticColors colors) {
        if (name.contentEquals("this") || name.contentEquals("super") || name.contentEquals("class")) {
            return;
        }
        var fromPath = getCurrentPath();
        var toEl = trees.getElement(fromPath);
        if (toEl == null) {
            return;
        }
        if (toEl.getKind() == ElementKind.FIELD) {
            // Find region containing name
            var pos = trees.getSourcePositions();
            var root = fromPath.getCompilationUnit();
            var leaf = fromPath.getLeaf();
            var start = (int) pos.getStartPosition(root, leaf);
            var end = (int) pos.getEndPosition(root, leaf);
            // Adjust start to remove LHS of declarations and member selections
            if (leaf instanceof MemberSelectTree) {
                var select = (MemberSelectTree) leaf;
                start = (int) pos.getEndPosition(root, select.getExpression());
            } else if (leaf instanceof VariableTree) {
                var declaration = (VariableTree) leaf;
                start = (int) pos.getEndPosition(root, declaration.getType());
            }
            // If no position, give up
            if (start == -1 || end == -1) {
                return;
            }
            // Find name inside expression
            var file = Paths.get(root.getSourceFile().toUri());
            var contents = FileStore.contents(file);
            var region = contents.substring(start, end);
            start += region.indexOf(name.toString());
            end = start + name.length();
            var range = RangeHelper.range(root, start, end);
            colors.fields.add(range);
            if (toEl.getModifiers().contains(Modifier.STATIC)) {
                colors.statics.add(range);
            }
        }
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, SemanticColors colors) {
        check(t.getName(), colors);
        return super.visitIdentifier(t, colors);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree t, SemanticColors colors) {
        check(t.getIdentifier(), colors);
        return super.visitMemberSelect(t, colors);
    }

    @Override
    public Void visitVariable(VariableTree t, SemanticColors colors) {
        check(t.getName(), colors);
        return super.visitVariable(t, colors);
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree t, SemanticColors colors) {
        return super.visitCompilationUnit(t, colors);
    }
}
