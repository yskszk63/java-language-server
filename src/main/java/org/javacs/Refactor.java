package org.javacs;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.javacs.lsp.*;

interface Refactor {
    boolean canRefactor(Diagnostic d);

    CodeAction refactor(Parser parse, TreePath error);

    Refactor[] RULES = { // TODO this is used!
        new PrependUnderscore(), new RemoveVar(),
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

    class RemoveVar implements Refactor {
        @Override
        public boolean canRefactor(Diagnostic d) {
            return d.code.equals("unused_local");
        }

        @Override
        public CodeAction refactor(Parser parse, TreePath error) {
            LOG.info("Try to remove LHS...");
            if (!(error.getLeaf() instanceof VariableTree)) {
                LOG.info("...not a variable");
                return CodeAction.NONE;
            }
            var file = error.getCompilationUnit().getSourceFile().toUri();
            var variable = (VariableTree) error.getLeaf();
            var expression = variable.getInitializer();
            var pos = parse.trees.getSourcePositions();
            var startVar = (int) pos.getStartPosition(parse.root, variable);
            var startRhs = (int) pos.getStartPosition(parse.root, expression);
            var delete = new Span(startVar, startRhs).asRange(parse.root.getLineMap());
            var edit = new TextEdit(delete, "");
            var a = new CodeAction();
            a.kind = CodeActionKind.QuickFix;
            a.title = "Remove variable";
            a.edit = new WorkspaceEdit();
            a.edit.changes = Map.of(file, List.of(edit));
            return a;
        }
    }

    Logger LOG = Logger.getLogger("main");
}
