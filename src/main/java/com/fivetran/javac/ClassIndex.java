package com.fivetran.javac;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remembers the tree of every class symbol so we can locate symbols later.
 */
public class ClassIndex extends BaseScanner {
    private static final Logger LOG = Logger.getLogger("");

    private final Map<Symbol.ClassSymbol, Declared> classes = new HashMap<>();

    private static class Declared {
        public final JCTree.JCCompilationUnit compilationUnit;
        public final JCTree.JCClassDecl classDeclaration;

        private Declared(JCTree.JCCompilationUnit compilationUnit, JCTree.JCClassDecl classDeclaration) {
            this.compilationUnit = compilationUnit;
            this.classDeclaration = classDeclaration;
        }
    }

    public ClassIndex(Context context) {
        super(context);

        context.put(ClassIndex.class, this);
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        super.visitClassDef(tree);

        classes.put(tree.sym, new Declared(compilationUnit, tree));
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        if (tree.getSourceFile().getKind() == JavaFileObject.Kind.SOURCE)
            super.visitTopLevel(tree);
    }

    public Optional<SymbolLocation> locate(Symbol symbol) {
        try {
            // Look up AST of class that declared this symbol
            Symbol.ClassSymbol declaringClass = symbol.enclClass();
            Declared declared = classes.get(declaringClass);

            if (declared == null)
                return Optional.empty();

            // Find declaration of symbol within AST
            JCTree declaration = TreeInfo.declarationFor(symbol, declared.classDeclaration);

            if (declaration == null)
                return Optional.empty();

            // Declaration should include offset
            int offset = declaration.pos;

            // If symbol is a class, offset points to 'class' keyword, not name
            // Find the name by searching the text of the source, starting at the 'class' keyword
            if (symbol instanceof Symbol.ClassSymbol) {
                CharSequence content = declared.compilationUnit.sourcefile.getCharContent(false);
                Name name = symbol.getSimpleName();

                offset = indexOf(content, name, offset);
            }

            int end = offset + symbol.name.length();

            return Optional.of(new SymbolLocation(declared.compilationUnit.sourcefile, offset, end));
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
