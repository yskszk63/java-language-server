package org.javacs;

import com.sun.javadoc.MethodDoc;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.sun.source.util.Trees;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import javax.lang.model.element.*;

class Signatures {
    static Optional<SignatureHelp> help(FocusedResult compiled, int line, int column) {
        long offset = compiled.compilationUnit.getLineMap().getPosition(line, column);

        return compiled.cursor.flatMap(c -> new Signatures(c, offset, compiled.task).get());
    }

    private final TreePath cursor;
    private final long cursorOffset;
    private final JavacTask task;

    private Signatures(TreePath cursor, long cursorOffset, JavacTask task) {
        this.cursor = cursor;
        this.cursorOffset = cursorOffset;
        this.task = task;
    }


    private Optional<SignatureHelp> get() {
        if (cursor.getLeaf().getKind() == Tree.Kind.METHOD_INVOCATION)
            return Optional.of(methodHelp((MethodInvocationTree) cursor.getLeaf()));
        if (cursor.getLeaf().getKind() == Tree.Kind.NEW_CLASS)
            return Optional.of(constructorHelp((NewClassTree) cursor.getLeaf()));
        if (cursor.getParentPath().getLeaf().getKind() == Tree.Kind.METHOD_INVOCATION)
            return Optional.of(methodHelp((MethodInvocationTree) cursor.getParentPath().getLeaf()));
        if (cursor.getParentPath().getLeaf().getKind() == Tree.Kind.NEW_CLASS)
            return Optional.of(constructorHelp((NewClassTree) cursor.getParentPath().getLeaf()));
        else
            return Optional.empty();
    }

    private SignatureHelp constructorHelp(NewClassTree leaf) {
        Trees trees = Trees.instance(task);
        TreePath identifierPath = TreePath.getPath(cursor.getCompilationUnit(), leaf.getIdentifier());
        Element classElement = trees.getElement(identifierPath);
        List<ExecutableElement> candidates = classElement.getEnclosedElements().stream()
                .filter(member -> member.getKind() == ElementKind.CONSTRUCTOR)
                .map(method -> (ExecutableElement) method)
                .collect(Collectors.toList());
        List<SignatureInformation> signatures = candidates.stream()
                .map(member -> methodInfo(member, false))
                .collect(Collectors.toList());
        int activeSignature = candidates.indexOf(classElement);

        return new SignatureHelp(
                signatures,
                activeSignature < 0 ? null : activeSignature,
                activeParameter(leaf.getArguments())
        );
    }

    private SignatureHelp methodHelp(MethodInvocationTree leaf) {
        Trees trees = Trees.instance(task);
        TreePath methodPath = TreePath.getPath(cursor.getCompilationUnit(), leaf.getMethodSelect());
        Element methodElement = trees.getElement(methodPath);
        Name name = methodElement.getSimpleName();
        List<ExecutableElement> candidates = methodElement.getEnclosingElement().getEnclosedElements().stream()
                .filter(member -> member.getKind() == ElementKind.METHOD && member.getSimpleName().equals(name))
                .map(method -> (ExecutableElement) method)
                .collect(Collectors.toList());
        List<SignatureInformation> signatures = candidates.stream()
                .map(member -> methodInfo(member, true))
                .collect(Collectors.toList());
        int activeSignature = candidates.indexOf(methodElement);

        return new SignatureHelp(
                signatures,
                activeSignature < 0 ? null : activeSignature,
                activeParameter(leaf.getArguments())
        );
    }

    private SignatureInformation methodInfo(ExecutableElement method, boolean showReturn) {
        Javadocs docs = Javadocs.global();
        Optional<MethodDoc> doc = docs.methodDoc(method);
        
        return new SignatureInformation(
                Hovers.methodSignature(method, showReturn, true),
                doc.map(MethodDoc::commentText).orElse(null),
                paramInfo(method)
        );
    }

    private List<ParameterInformation> paramInfo(ExecutableElement method) {
        List<ParameterInformation> params = new ArrayList<>();

        int i = 0;
        for (VariableElement var : method.getParameters()) {
            boolean varargs = method.isVarArgs() && i == method.getParameters().size() - 1;

            params.add(new ParameterInformation(
                    Hovers.shortName(var, varargs),
                    task.getElements().getDocComment(var)
            ));

            i++;
        }
        return params;
    }

    private Integer activeParameter(List<? extends ExpressionTree> arguments) {
        SourcePositions pos = Trees.instance(task).getSourcePositions();

        int i = 0;
        for (ExpressionTree arg : arguments) {
            if (pos.getEndPosition(cursor.getCompilationUnit(), arg) >= cursorOffset)
                return i;

            i++;
        }

        return null;
    }
}