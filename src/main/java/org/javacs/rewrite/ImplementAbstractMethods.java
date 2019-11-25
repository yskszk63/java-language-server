package org.javacs.rewrite;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
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
            var trees = Trees.instance(task.task);
            var type = elements.getTypeElement(className);
            var dest = trees.getTree(type);
            var indent = EditHelper.indent(task.task, task.root(), dest) + 4;
            for (var member : elements.getAllMembers(type)) {
                if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.ABSTRACT)) {
                    var method = (ExecutableElement) member;
                    var source = findSource(compiler, method);
                    var text = printImplementation(method, source);
                    text = text.replaceAll("\n", "\n" + " ".repeat(indent));
                    insertText.add(text);
                }
            }
            var insert = EditHelper.insertAtEndOfClass(task.task, task.root(), dest);
            TextEdit[] edits = {new TextEdit(new Range(insert, insert), insertText + "\n")};
            return Map.of(file, edits);
        }
    }

    private String printImplementation(ExecutableElement method, MethodTree source) {
        var buf = new StringBuilder();
        buf.append("\n@Override\n");
        if (method.getModifiers().contains(Modifier.PUBLIC)) {
            buf.append("public ");
        }
        if (method.getModifiers().contains(Modifier.PROTECTED)) {
            buf.append("protected ");
        }
        buf.append(printType(method.getReturnType())).append(" ");
        buf.append(method.getSimpleName()).append("(");
        buf.append(printParameters(method, source));
        buf.append(") {\n    // TODO\n}");
        return buf.toString();
    }

    private String printType(TypeMirror type) {
        if (type instanceof DeclaredType) {
            var declared = (DeclaredType) type;
            return declared.asElement().getSimpleName().toString();
        } else if (type instanceof ArrayType) {
            var array = (ArrayType) type;
            return printType(array.getComponentType()) + "[]";
        } else {
            return type.toString();
        }
    }

    private String printParameters(ExecutableElement method, MethodTree source) {
        var join = new StringJoiner(", ");
        for (var i = 0; i < method.getParameters().size(); i++) {
            var type = printType(method.getParameters().get(i).asType());
            var name = source.getParameters().get(i).getName();
            join.add(type + " " + name);
        }
        return join.toString();
    }

    private MethodTree findSource(CompilerProvider compiler, ExecutableElement method) {
        var parent = (TypeElement) method.getEnclosingElement();
        // TODO sometimes, the source will not be available
        var file = compiler.findAnywhere(parent.getQualifiedName().toString()).get();
        var parse = compiler.parse(file);
        return new FindBestMethod().scan(parse.root, method);
    }
}
