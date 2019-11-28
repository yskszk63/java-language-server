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
import org.javacs.CompileTask;
import org.javacs.CompilerProvider;
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
        try (var task = compiler.compile(file)) {
            var typeElement = task.task.getElements().getTypeElement(className);
            var typePath = Trees.instance(task.task).getPath(typeElement);
            var typeTree = (ClassTree) typePath.getLeaf();
            var fields = fieldsNeedingInitialization(typeTree);
            var parameters = generateParameters(task, fields);
            var initializers = generateInitializers(task, fields);
            var template = TEMPLATE;
            template = template.replace("$class", simpleName(className));
            template = template.replace("$parameters", parameters);
            template = template.replace("$initializers", initializers);
            var indent = EditHelper.indent(task.task, task.root(), typeTree) + 4;
            template = template.replaceAll("\n", "\n" + " ".repeat(indent));
            template = template + "\n\n";
            var insert = insertPoint(task, typeTree);
            TextEdit[] edits = {new TextEdit(new Range(insert, insert), template)};
            return Map.of(file, edits);
        }
    }

    private List<VariableTree> fieldsNeedingInitialization(ClassTree typeTree) {
        var fields = new ArrayList<VariableTree>();
        for (var member : typeTree.getMembers()) {
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

    private String generateParameters(CompileTask task, List<VariableTree> fields) {
        var join = new StringJoiner(", ");
        for (var f : fields) {
            join.add(extract(task, f.getType()) + " " + f.getName());
        }
        return join.toString();
    }

    private String generateInitializers(CompileTask task, List<VariableTree> fields) {
        var join = new StringJoiner("\n    ");
        for (var f : fields) {
            join.add("this." + f.getName() + " = " + f.getName() + ";");
        }
        return join.toString();
    }

    private CharSequence extract(CompileTask task, Tree typeTree) {
        try {
            var contents = task.root().getSourceFile().getCharContent(true);
            var pos = Trees.instance(task.task).getSourcePositions();
            var start = (int) pos.getStartPosition(task.root(), typeTree);
            var end = (int) pos.getEndPosition(task.root(), typeTree);
            return contents.subSequence(start, end);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String simpleName(String className) {
        var dot = className.lastIndexOf('.');
        if (dot != -1) {
            return className.substring(dot + 1);
        }
        return className;
    }

    private Position insertPoint(CompileTask task, ClassTree typeTree) {
        for (var member : typeTree.getMembers()) {
            if (!(member instanceof VariableTree)) {
                return EditHelper.insertBefore(task.task, task.root(), member);
            }
        }
        return EditHelper.insertAtEndOfClass(task.task, task.root(), typeTree);
    }
}
