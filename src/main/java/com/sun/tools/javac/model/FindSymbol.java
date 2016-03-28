package com.sun.tools.javac.model;

import com.fivetran.javac.SymbolLocation;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class FindSymbol {
    private static final Logger LOG = Logger.getLogger("");

    public static Optional<SymbolLocation> locate(Context context, Symbol symbol)  {
        try {
            JavacElements elements = JavacElements.instance(context);
            JavacSourcePosition p = elements.getSourcePosition(symbol);

            if (p == null)
                return Optional.empty();
            else if (symbol instanceof Symbol.ClassSymbol) {
                JavaFileObject file = p.getFile();
                CharSequence content = p.sourcefile.getCharContent(false);
                Name name = symbol.getSimpleName();
                int offset = indexOf(content, name, p.getOffset());
                int end = offset + name.length();

                return Optional.of(new SymbolLocation(file, offset, end));
            }
            else {
                JavaFileObject file = p.getFile();
                int offset = p.getOffset();
                int end = offset + symbol.name.length();

                return Optional.of(new SymbolLocation(file, offset, end));
            }
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
    private static int indexOf(CharSequence source, CharSequence target, int fromIndex) {
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
