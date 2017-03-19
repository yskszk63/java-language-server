package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;

/**
 * Fix up the tree to make it easier to autocomplete, index
 */
public class TreePruner {
    public final JCTree.JCCompilationUnit tree;
    private final Context context;

    public TreePruner(JCTree.JCCompilationUnit tree, Context context) {
        this.tree = tree;
        this.context = context;
    }

    /**
     * Remove all statements after the statement the cursor is in
     */
    public TreePruner removeStatementsAfterCursor(long cursor) {
        tree.accept(new AutocompletePruner(tree.getSourceFile(), cursor, context));

        return this;
    }

    /**
     * Insert ';' after the users cursor so we recover from parse errors in a helpful way when doing autocomplete.
     */
    public static JavaFileObject putSemicolonAfterCursor(JavaFileObject file, URI path, long cursor) {
        try (Reader reader = file.openReader(true)) {
            StringBuilder acc = new StringBuilder();

            for (int i = 0; i < cursor; i++) {
                int next = reader.read();

                if (next == -1)
                    throw new RuntimeException("End of file " + file + " before cursor " + cursor);

                acc.append((char) next);
            }

            acc.append(";");

            for (int next = reader.read(); next > 0; next = reader.read()) {
                acc.append((char) next);
            }

            return new StringFileObject(acc.toString(), path);
        } catch (IOException e) {
            throw ShowMessageException.error("Error reading " + file, e);
        }
    }
}
