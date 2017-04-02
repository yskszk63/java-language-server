package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;

/**
 * Fix up the tree to make it easier to autocomplete, index
 */
public class TreePruner {
    private final JavacTask task;

    public TreePruner(JavacTask task) {
        this.task = task;
    }

    /**
     * Remove all statements after the statement the cursor is in
     */
    public void removeStatementsAfterCursor(CompilationUnitTree tree, int line, int character) {
        new CursorScanner<Void>(task, line, character) {
            @Override
            public Void visitBlock(BlockTree node, Void aVoid) {
                JCTree.JCBlock impl = (JCTree.JCBlock) node;

                impl.stats = pruneStatements(impl.stats);

                return super.visitBlock(node, aVoid);
            }

            @Override
            public Void visitSwitch(SwitchTree node, Void aVoid) {
                JCTree.JCSwitch impl = (JCTree.JCSwitch) node;

                impl.cases = pruneStatements(impl.cases);

                return super.visitSwitch(node, aVoid);
            }

            @Override
            public Void visitCase(CaseTree node, Void aVoid) {
                JCTree.JCCase impl = (JCTree.JCCase) node;

                impl.stats = pruneStatements(impl.stats);

                return super.visitCase(node, aVoid);
            }

            private <T extends Tree> List<T> pruneStatements(List<T> stats) {
                int countStatements = 0;
                boolean foundCursor = false;

                // Scan up to statement containing cursor
                while (countStatements < stats.size()) {
                    T s = stats.get(countStatements);

                    if (containsCursor(s))
                        foundCursor = true;
                    else if (foundCursor)
                        break;
                    else
                        this.scan(s, null);

                    countStatements++;
                }

                // Remove all statements after statement containing cursor
                return stats.take(countStatements);
            }
        }.apply(tree);
    }

    /**
     * Insert ';' after the users cursor so we recover from parse errors in a helpful way when doing autocomplete.
     */
    public static JavaFileObject putSemicolonAfterCursor(JavaFileObject file, int cursorLine, int cursorCharacter) {
        try (Reader reader = file.openReader(true)) {
            StringBuilder acc = new StringBuilder();
            int line = 1, character = 1;

            // Go to the cursor
            while (line < cursorLine || character < cursorCharacter) {
                int next = reader.read();

                if (next == -1)
                    throw new RuntimeException("End of file " + file + " before cursor " + cursorLine + ":" + cursorCharacter);
                else if (next == '\n') {
                    line++;
                    character = 1;
                }
                else
                    character++;

                acc.append((char) next);
            }

            // Go to the end of the line
            while (true) {
                int next = reader.read();

                if (next == -1 || next == '\n')
                    break;

                acc.append((char) next);
            }

            acc.append(";\n");

            for (int next = reader.read(); next > 0; next = reader.read()) {
                acc.append((char) next);
            }

            return new StringFileObject(acc.toString(), file.toUri());
        } catch (IOException e) {
            throw ShowMessageException.error("Error reading " + file, e);
        }
    }
}
