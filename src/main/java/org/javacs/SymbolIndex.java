package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Name;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

import javax.lang.model.element.ElementKind;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Global index of exported symbol declarations and references.
 * such as classes, methods, and fields.
 */
public class SymbolIndex {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Source path files, for which we support methods and classes
     */
    private Map<URI, SourceFileIndex> sourcePath = new HashMap<>();

    /**
     * Search all indexed symbols
     */
    public Stream<? extends SymbolInformation> search(String query) {
        Stream<SymbolInformation> classes = allSymbols(ElementKind.CLASS);
        Stream<SymbolInformation> methods = allSymbols(ElementKind.METHOD);

        return Stream.concat(classes, methods)
                     .filter(s -> containsCharsInOrder(s.getName(), query));
    }

    /**
     * Get all symbols in an open file
     */
    public Stream<? extends SymbolInformation> allInFile(URI source) { 
        SourceFileIndex index = sourcePath.getOrDefault(source, new SourceFileIndex());
        
        return index.declarations.values().stream().flatMap(map -> map.values().stream());
    }

    private Stream<SymbolInformation> allSymbols(ElementKind kind) {
        return sourcePath.values().stream().flatMap(f -> allSymbolsInFile(f, kind));
    }

    private Stream<SymbolInformation> allSymbolsInFile(SourceFileIndex f, ElementKind kind) {
        return f.declarations.getOrDefault(kind, Collections.emptyMap())
                             .values()
                             .stream();
    }

    /**
     * Find all references to a symbol
     */
    public Stream<? extends Location> references(Symbol symbol) {
        String key = uniqueName(symbol);

        return sourcePath.values().stream().flatMap(f -> {
            Map<String, Set<Location>> bySymbol = f.references.getOrDefault(symbol.getKind(), Collections.emptyMap());
            Set<Location> locations = bySymbol.getOrDefault(key, Collections.emptySet());

            return locations.stream();
        });
    }

    public Optional<SymbolInformation> findSymbol(Symbol symbol) {
        ElementKind kind = symbol.getKind();
        String key = uniqueName(symbol);

        for (SourceFileIndex f : sourcePath.values()) {
            Map<String, SymbolInformation> withKind = f.declarations.getOrDefault(kind, Collections.emptyMap());

            if (withKind.containsKey(key))
                return Optional.of(withKind.get(key));
        }

        return Optional.empty();
    }

    public static String uniqueName(Symbol s) {
        StringJoiner acc = new StringJoiner(".");

        createUniqueName(s, acc);

        return acc.toString();
    }

    /**
     * Check if name contains all the characters of query in order.
     * For example, name 'FooBar' contains query 'FB', but not 'BF'
     */
    private boolean containsCharsInOrder(String name, String query) {
        int iName = 0, iQuery = 0;

        while (iName < name.length() && iQuery < query.length()) {
            // If query matches name, consume a character of query and of name
            if (name.charAt(iName) == query.charAt(iQuery)) {
                iName++;
                iQuery++;
            }
            // Otherwise consume a character of name
            else {
                iName++;
            }
        }

        // If the entire query was consumed, we found what we were looking for
        return iQuery == query.length();
    }

    public static boolean shouldIndex(Symbol symbol) {
        ElementKind kind = symbol.getKind();

        switch (kind) {
            case ENUM:
            case ANNOTATION_TYPE:
            case INTERFACE:
            case ENUM_CONSTANT:
            case FIELD:
            case METHOD:
                return true;
            case CLASS:
                return !symbol.isAnonymous();
            case CONSTRUCTOR:
                // TODO also skip generated constructors
                return !symbol.getEnclosingElement().isAnonymous();
            default:
                return false;
        }
    }

    public static Location location(JCTree tree, JCTree.JCCompilationUnit compilationUnit) {
        try {
            // Declaration should include offset
            int offset = tree.pos;
            int end = tree.getEndPosition(null);

            // If symbol is a class, offset points to 'class' keyword, not name
            // Find the name by searching the text of the source, starting at the 'class' keyword
            if (tree instanceof JCTree.JCClassDecl) {
                Symbol.ClassSymbol symbol = ((JCTree.JCClassDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }
            else if (tree instanceof JCTree.JCMethodDecl) {
                Symbol.MethodSymbol symbol = ((JCTree.JCMethodDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }
            else if (tree instanceof JCTree.JCVariableDecl) {
                Symbol.VarSymbol symbol = ((JCTree.JCVariableDecl) tree).sym;
                offset = offset(compilationUnit, symbol, offset);
                end = offset + symbol.name.length();
            }

            Range position = JavaLanguageServer.findRange(compilationUnit.getSourceFile(),
                                                                 offset,
                                                                 end);
            Location location = new Location();

            location.setUri(compilationUnit.getSourceFile().toUri().toString());
            location.setRange(position);

            return location;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * All references to symbol in compilationUnit, including things like local variables that aren't indexe
     */
    public static List<Location> nonIndexedReferences(final Symbol symbol, final JCTree.JCCompilationUnit compilationUnit) {
        List<Location> result = new ArrayList<>();

        compilationUnit.accept(new TreeScanner() {
            @Override
            public void visitSelect(JCTree.JCFieldAccess tree) {
                super.visitSelect(tree);

                if (tree.sym != null && tree.sym.equals(symbol))
                    result.add(SymbolIndex.location(tree, compilationUnit));
            }

            @Override
            public void visitReference(JCTree.JCMemberReference tree) {
                super.visitReference(tree);

                if (tree.sym != null && tree.sym.equals(symbol))
                    result.add(SymbolIndex.location(tree, compilationUnit));
            }

            @Override
            public void visitIdent(JCTree.JCIdent tree) {
                super.visitIdent(tree);

                if (tree.sym != null && tree.sym.equals(symbol))
                    result.add(SymbolIndex.location(tree, compilationUnit));
            }
        });

        return result;
    }

    private static int offset(JCTree.JCCompilationUnit compilationUnit,
                              Symbol symbol,
                              int estimate) throws IOException {
        CharSequence content = compilationUnit.sourcefile.getCharContent(false);
        Name name = symbol.getSimpleName();

        estimate = indexOf(content, name, estimate);
        return estimate;
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

    private static void createUniqueName(Symbol s, StringJoiner acc) {
        if (s != null) {
            createUniqueName(s.owner, acc);

            if (!s.getSimpleName().isEmpty())
                acc.add(s.getSimpleName().toString());
        }
    }

    public void put(URI uri, SourceFileIndex index) {
        sourcePath.put(uri, index);
    }

    /**
     * Clear a file from the index when it is deleted
     */
    public void clear(URI sourceFile) {
        sourcePath.remove(sourceFile);
    }
}