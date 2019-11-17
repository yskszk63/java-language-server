package org.javacs;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import org.javacs.lsp.*;

interface Refactor {
    boolean canRefactor(Diagnostic d);

    CodeAction refactor(Parser parse, TreePath error, String message);

    Refactor[] RULES = { // TODO this is used!
        new ConvertToStatement(), new ConvertToBlock(), new RemoveDeclaration(), new SuppressWarning(), new AddThrows(),
    };

    class ConvertToStatement implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("unused_local");
        }

        /** https://docs.oracle.com/javase/specs/jls/se13/html/jls-14.html#jls-14.8 */
        static boolean isExpressionStatement(Tree t) {
            switch (t.getKind()) {
                case ASSIGNMENT:
                case PREFIX_INCREMENT:
                case PREFIX_DECREMENT:
                case POSTFIX_INCREMENT:
                case POSTFIX_DECREMENT:
                case METHOD_INVOCATION:
                case NEW_CLASS:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error, String message) {
            if (!(error.getLeaf() instanceof VariableTree)) {
                return CodeAction.NONE;
            }
            var variable = (VariableTree) error.getLeaf();
            var expression = variable.getInitializer();
            if (!isExpressionStatement(expression)) {
                return CodeAction.NONE;
            }
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var pos = parse.trees.getSourcePositions();
            var startVar = (int) pos.getStartPosition(parse.root, variable);
            var startRhs = (int) pos.getStartPosition(parse.root, expression);
            var delete = new Span(startVar, startRhs).asRange(parse.root.getLineMap());
            var edit = new TextEdit(delete, "");
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Convert to statement";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(edit));
            return a;
        }
    }

    class ConvertToBlock implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("unused_field");
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error, String message) {
            if (!(error.getLeaf() instanceof VariableTree)) {
                return CodeAction.NONE;
            }
            var variable = (VariableTree) error.getLeaf();
            var expression = variable.getInitializer();
            if (expression == null) {
                return CodeAction.NONE;
            }
            if (!ConvertToStatement.isExpressionStatement(expression)) {
                return CodeAction.NONE;
            }
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var pos = parse.trees.getSourcePositions();
            var startVar = (int) pos.getStartPosition(parse.root, variable);
            var startRhs = (int) pos.getStartPosition(parse.root, expression);
            var deleteLhs = new Span(startVar, startRhs).asRange(parse.root.getLineMap());
            var fixLhs = new TextEdit(deleteLhs, "{ ");
            if (variable.getModifiers().getFlags().contains(Modifier.STATIC)) {
                fixLhs.newText = "static {";
            }
            var right = (int) pos.getEndPosition(parse.root, variable);
            var insertRight = new Span(right, right).asRange(parse.root.getLineMap());
            var fixRhs = new TextEdit(insertRight, " }");
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Convert to block";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(fixLhs, fixRhs));
            return a;
        }
    }

    class RemoveDeclaration implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("unused_class") || d.code.equals("unused_method");
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error, String message) {
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var pos = parse.trees.getSourcePositions();
            var start = (int) pos.getStartPosition(error.getCompilationUnit(), error.getLeaf());
            var end = (int) pos.getEndPosition(error.getCompilationUnit(), error.getLeaf());
            var range = new Span(start, end).asRange(parse.root.getLineMap());
            var delete = new TextEdit(range, "");
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Remove declaration";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(delete));
            return a;
        }
    }

    class SuppressWarning implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("compiler.warn.unchecked.call.mbr.of.raw.type");
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error, String message) {
            while (!(error.getLeaf() instanceof MethodTree)) {
                error = error.getParentPath();
                if (error == null) return CodeAction.NONE;
            }
            var pos = parse.trees.getSourcePositions();
            var startMethod = (int) pos.getStartPosition(error.getCompilationUnit(), error.getLeaf());
            var lines = parse.root.getLineMap();
            var line = lines.getLineNumber(startMethod);
            var startLine = (int) lines.getStartPosition(line);
            var indent = " ".repeat(startMethod - startLine);
            var insertText = "@SuppressWarnings(\"unchecked\")\n" + indent;
            var insertPoint = new Span(startMethod, startMethod).asRange(parse.root.getLineMap());
            var insert = new TextEdit(insertPoint, insertText);
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Suppress 'unchecked' warning";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(insert));
            return a;
        }
    }

    class AddThrows implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("compiler.err.unreported.exception.need.to.catch.or.throw");
        }

        private static final Pattern unreported = Pattern.compile("unreported exception ((\\w+\\.)*\\w+)");

        private String extractExceptionName(String message) {
            var matcher = unreported.matcher(message);
            if (!matcher.find()) {
                LOG.warning(String.format("`%s` doesn't match `%s`", message, unreported));
                return "";
            }
            return matcher.group(1);
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error, String message) {
            var qualifiedName = extractExceptionName(message);
            var packageName = StringSearch.mostName(qualifiedName); // TODO add import
            var simpleName = StringSearch.lastName(qualifiedName);
            if (qualifiedName.isEmpty()) {
                return CodeAction.NONE;
            }
            while (!(error.getLeaf() instanceof MethodTree)) {
                error = error.getParentPath();
                if (error == null) return CodeAction.NONE;
            }
            var method = (MethodTree) error.getLeaf();
            var pos = parse.trees.getSourcePositions();
            var startBody = (int) pos.getStartPosition(error.getCompilationUnit(), method.getBody());
            var insertPoint = new Span(startBody, startBody).asRange(parse.root.getLineMap());
            String insertText;
            if (method.getThrows().isEmpty()) {
                insertText = "throws " + simpleName + " ";
            } else {
                insertText = ", " + simpleName + " ";
            }
            var insert = new TextEdit(insertPoint, insertText);
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Add 'throws'";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(insert));
            return a;
        }
    }

    final Logger LOG = Logger.getLogger("main");
}
