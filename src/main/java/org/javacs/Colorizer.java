package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.net.URI;
import java.util.*;
import javax.lang.model.element.*;
import org.javacs.lsp.*;

class Colorizer extends TreePathScanner<Void, Void> {
    private final Trees trees;
    final SemanticColors colors = new SemanticColors();

    Colorizer(JavacTask task) {
        trees = Trees.instance(task);
    }

    private void check(Name name) {
        if (name.contentEquals("this") || name.contentEquals("super")) {
            return;
        }
        var fromPath = getCurrentPath();
        var toEl = trees.getElement(fromPath);
        if (toEl == null) {
            return;
        }
        if (toEl.getKind() == ElementKind.FIELD) {
            var pos = trees.getSourcePositions();
            var root = fromPath.getCompilationUnit();
            var leaf = fromPath.getLeaf();
            var lines = root.getLineMap();
            var start = (int) pos.getStartPosition(root, leaf);
            var end = (int) pos.getEndPosition(root, leaf);
            var contents = FileStore.contents(colors.uri);
            var region = contents.substring(start, end);
            start += region.lastIndexOf(name.toString());
            end = start + name.length();
            var startLine = (int) lines.getLineNumber(start);
            var startColumn = (int) lines.getColumnNumber(start);
            var endLine = (int) lines.getLineNumber(end);
            var endColumn = (int) lines.getColumnNumber(end);
            var startPos = new Position(startLine, startColumn);
            var endPos = new Position(endLine, endColumn);
            var range = new Range(startPos, endPos);
            colors.fields.add(range);
            if (toEl.getModifiers().contains(Modifier.STATIC)) {
                colors.statics.add(range);
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
        colors.uri = t.getSourceFile().toUri();
        return super.visitCompilationUnit(t, null);
    }
}

class SemanticColors {
    URI uri;
    List<Range> statics = new ArrayList<>(), fields = new ArrayList<>();
}
