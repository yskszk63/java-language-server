package org.javacs.rewrite;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import javax.lang.model.element.Modifier;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

public class GenerateRecordConstructor implements Rewrite {
    final String className;

    public GenerateRecordConstructor(String className) {
        this.className = className;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        // TODO this needs to fall back on looking for inner classes and package-private classes
        var file = compiler.findTopLevelDeclaration(className);
        var task = compiler.parse(file);
        var leaf = new FindClassDeclarationNamed().scan(task.root, className);
        var fields = fieldsNeedingInitialization(leaf);
        var parameters = generateParameters(task, fields);
        var initializers = generateInitializers(task, fields);
        var template = TEMPLATE;
        template = template.replace("$class", simpleName(className));
        template = template.replace("$parameters", parameters);
        template = template.replace("$initializers", initializers);
        var indent = indent(task, leaf) + 4;
        template = template.replaceAll("\n", "\n" + " ".repeat(indent));
        template = template + "\n\n";
        var insert = insertPoint(task, leaf);
        TextEdit[] edits = {new TextEdit(new Range(insert, insert), template)};
        return Map.of(file, edits);
    }

    private List<VariableTree> fieldsNeedingInitialization(ClassTree leaf) {
        var fields = new ArrayList<VariableTree>();
        for (var member : leaf.getMembers()) {
            if (!(member instanceof VariableTree)) continue;
            var field = (VariableTree) member;
            if (field.getInitializer() != null) continue;
            var flags = field.getModifiers().getFlags();
            if (flags.contains(Modifier.STATIC)) continue;
            if (!flags.contains(Modifier.FINAL)) continue;
            fields.add(field);
        }
        return fields;
    }

    private static final String TEMPLATE = "\n$class($parameters) {\n    $initializers\n}";

    private String generateParameters(ParseTask task, List<VariableTree> fields) {
        var join = new StringJoiner(", ");
        for (var f : fields) {
            join.add(extract(task, f.getType()) + " " + f.getName());
        }
        return join.toString();
    }

    private String generateInitializers(ParseTask task, List<VariableTree> fields) {
        var join = new StringJoiner("\n    ");
        for (var f : fields) {
            join.add("this." + f.getName() + " = " + f.getName() + ";");
        }
        return join.toString();
    }

    private CharSequence extract(ParseTask task, Tree leaf) {
        try {
            var contents = task.root.getSourceFile().getCharContent(true);
            var pos = Trees.instance(task.task).getSourcePositions();
            var start = (int) pos.getStartPosition(task.root, leaf);
            var end = (int) pos.getEndPosition(task.root, leaf);
            return contents.subSequence(start, end);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int indent(ParseTask task, ClassTree leaf) {
        var pos = Trees.instance(task.task).getSourcePositions();
        var lines = task.root.getLineMap();
        var startClass = pos.getStartPosition(task.root, leaf);
        var startLine = lines.getStartPosition(lines.getLineNumber(startClass));
        return (int) (startClass - startLine);
    }

    private String simpleName(String className) {
        var dot = className.lastIndexOf('.');
        if (dot != -1) {
            return className.substring(dot + 1);
        }
        return className;
    }

    private Position insertPoint(ParseTask task, ClassTree leaf) {
        for (var member : leaf.getMembers()) {
            if (!(member instanceof VariableTree)) {
                return insertBefore(task, member);
            }
        }
        return insertAtEndOfClass(task, leaf);
    }

    private Position insertBefore(ParseTask task, Tree member) {
        var pos = Trees.instance(task.task).getSourcePositions();
        var lines = task.root.getLineMap();
        var start = pos.getStartPosition(task.root, member);
        var line = (int) lines.getLineNumber(start);
        return new Position(line - 1, 0);
    }

    private Position insertAtEndOfClass(ParseTask task, ClassTree leaf) {
        var pos = Trees.instance(task.task).getSourcePositions();
        var lines = task.root.getLineMap();
        var end = pos.getEndPosition(task.root, leaf);
        var line = (int) lines.getLineNumber(end);
        return new Position(line - 1, 0);
    }
}
