package com.fivetran.javac;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.FindSymbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GotoDefinitionVisitor extends CursorScanner {
    private static final Logger LOG = Logger.getLogger("");

    public final Set<SymbolLocation> definitions = new HashSet<>();

    public GotoDefinitionVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    @Override
    public void visitIdent(JCTree.JCIdent id) {
        super.visitIdent(id);

        if (!containsCursor(id))
            return;

        FindSymbol.locate(context, id.sym).map(definitions::add);
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        super.visitSelect(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(tree.getExpression()))
            super.visitSelect(tree);
        else
            FindSymbol.locate(context, tree.sym).map(definitions::add);
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);

        // Given a member reference [expr]::[name]
        // expr is taken care of by visitIdentifier
        // Check cursor is in name
        if (containsCursor(tree.getQualifierExpression()))
            super.visitReference(tree);
        else
            FindSymbol.locate(context, tree.sym).map(definitions::add);
    }

    private Optional<SymbolLocation> getLocation(Symbol symbol) {
        try {
            JavacTrees trees = JavacTrees.instance(context);
            JavaFileObject file = compilationUnit.getSourceFile();
            JCTree tree = trees.getTree(symbol);
            long start = trees.getSourcePositions().getStartPosition(compilationUnit, tree);
            long end = trees.getSourcePositions().getEndPosition(compilationUnit, tree);
            JavacElements elements = JavacElements.instance(context);

            // If class symbol, javac points to 'class' keyword, not name
            // Find the class name by scanning the source, starting at 'class' keyword
            if (symbol instanceof Symbol.ClassSymbol) {
                CharSequence content = file.getCharContent(false);
                Name name = symbol.getSimpleName();
                int offset = indexOf(content, name, (int) start);

                return Optional.of(new SymbolLocation(file, offset, offset + name.length()));
            }
            else
                return Optional.of(new SymbolLocation(file, start, end));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error getting location of symbol " + symbol, e);

            return Optional.empty();
        }
    }
    /**
     * Adapted from java.util.String.
     *
     * The source is the character array being searched, and the target
     * is the string being searched for.
     *
     * @param   source       the characters being searched.
     * @param   target       the characters being searched for.
     * @param   fromIndex    the index to begin searching from.
     */
    static int indexOf(CharSequence source, CharSequence target, int fromIndex) {
        int sourceOffset = 0, sourceCount = source.length(), targetOffset = 0, targetCount = target.length();

        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            return fromIndex;
        }

        char first = target.charAt(targetOffset);
        int max = sourceOffset + (sourceCount - targetCount);

        for (int i = sourceOffset + fromIndex; i <= max; i++) {
            /* Look for first character. */
            if (source.charAt(i) != first) {
                while (++i <= max && source.charAt(i) != first);
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + targetCount - 1;
                for (int k = targetOffset + 1; j < end && source.charAt(j) == target.charAt(k); j++, k++);

                if (j == end) {
                    /* Found whole string. */
                    return i - sourceOffset;
                }
            }
        }
        return -1;
    }
}
