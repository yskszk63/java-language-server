package org.javacs;

import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Map;
import org.javacs.lsp.*;

interface Refactor {
    boolean canRefactor(Diagnostic d);

    CodeAction refactor(Parser parse, TreePath error);

    Refactor[] RULES = { // TODO this is used!
        new PrependUnderscore(), new ConvertToStatement(),
    };

    class PrependUnderscore implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("unused_param");
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error) {
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var edit = new TextEdit(parse.range(error), "_");
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Prefix with underscore";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(edit));
            return a;
        }
    }

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
        public CodeAction refactor(Parser parse, TreePath error) {
            if (!(error.getLeaf() instanceof VariableTree)) {
                return CodeAction.NONE;
            }
            var variable = (VariableTree) error.getLeaf();
            if (!isExpressionStatement(variable.getInitializer())) {
                return CodeAction.NONE;
            }
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var expression = variable.getInitializer();
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
}
