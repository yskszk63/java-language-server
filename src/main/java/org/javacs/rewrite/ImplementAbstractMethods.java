package org.javacs.rewrite;

import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

public class ImplementAbstractMethods implements Rewrite {
    final String className;

    public ImplementAbstractMethods(String className) {
        this.className = className;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        var file = compiler.findTopLevelDeclaration(className);
        var insertText = new StringJoiner("\n");
        try (var task = compiler.compile(file)) {
            var elements = task.task.getElements();
            var types = task.task.getTypes();
            var trees = Trees.instance(task.task);
            var thisClass = elements.getTypeElement(className);
            var thisTree = trees.getTree(thisClass);
            var indent = EditHelper.indent(task.task, task.root(), thisTree) + 4;
            for (var member : elements.getAllMembers(thisClass)) {
                if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.ABSTRACT)) {
                    var method = (ExecutableElement) member;
                    var parameterizedType =
                            (ExecutableType) types.asMemberOf((DeclaredType) thisClass.asType(), method);
                    var text = EditHelper.printMethod(compiler, method, parameterizedType);
                    text = text.replaceAll("\n", "\n" + " ".repeat(indent));
                    insertText.add(text);
                }
            }
            var insert = EditHelper.insertAtEndOfClass(task.task, task.root(), thisTree);
            TextEdit[] edits = {new TextEdit(new Range(insert, insert), insertText + "\n")};
            return Map.of(file, edits);
        }
    }
}
