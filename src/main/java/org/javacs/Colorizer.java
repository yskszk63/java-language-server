package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import javax.lang.model.element.*;
import org.javacs.lsp.*;

class Colorizer extends TreePathScanner<Void, Void> {
    private final Trees trees;
    final ColorsHolder colors = new ColorsHolder();

    Colorizer(JavacTask task) {
        trees = Trees.instance(task);
    }

    private void check(Name name) {
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
            var span = new Span(start, end);
            colors.fields.add(span);
            if (toEl.getModifiers().contains(Modifier.STATIC)) {
                colors.statics.add(span);
            }
        }
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, Void __) {
        check(t.getName());
        return super.visitIdentifier(t, null);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree t, Void __) {
        check(t.getIdentifier());
        return super.visitMemberSelect(t, null);
    }

    @Override
    public Void visitVariable(VariableTree t, Void __) {
        check(t.getName());
        return super.visitVariable(t, null);
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree t, Void __) {
        return super.visitCompilationUnit(t, null);
    }
}

class ColorsHolder {
    List<Span> statics = new ArrayList<>(), fields = new ArrayList<>();
}

class SemanticColors {
    URI uri;
    List<Range> statics = new ArrayList<>(), fields = new ArrayList<>();
}
