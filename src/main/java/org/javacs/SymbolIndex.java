package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import org.eclipse.lsp4j.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.tools.Diagnostic;
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
    public Stream<SymbolInformation> search(String query) {
        Stream<SymbolInformation> classes = allSymbols(ElementKind.CLASS);
        Stream<SymbolInformation> methods = allSymbols(ElementKind.METHOD);

        return Stream.concat(classes, methods)
                     .filter(s -> containsCharsInOrder(s.getName(), query));
    }

    /**
     * Get all symbols in an open file
     */
    public Stream<SymbolInformation> allInFile(URI source) {
        SourceFileIndex index = sourcePath.getOrDefault(source, new SourceFileIndex());

        return index.declarations.values().stream().flatMap(map -> map.values().stream());
    }

    /**
     * All indexed symbols of a kind
     */
    public Stream<SymbolInformation> allSymbols(ElementKind kind) {
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
    public Stream<Location> references(Element symbol) {
        String key = qualifiedName(symbol);

        return sourcePath.values().stream().flatMap(f -> {
            Map<String, Set<Location>> bySymbol = f.references.getOrDefault(symbol.getKind(), Collections.emptyMap());
            Set<Location> locations = bySymbol.getOrDefault(key, Collections.emptySet());

            return locations.stream();
        });
    }

    public Optional<SymbolInformation> find(Element symbol) {
        ElementKind kind = symbol.getKind();
        String key = qualifiedName(symbol);

        for (SourceFileIndex f : sourcePath.values()) {
            Map<String, SymbolInformation> withKind = f.declarations.getOrDefault(kind, Collections.emptyMap());

            if (withKind.containsKey(key))
                return Optional.of(withKind.get(key));
        }

        return Optional.empty();
    }

    private static String qualifiedName(Element s) {
        StringJoiner acc = new StringJoiner(".");

        createQualifiedName(s, acc);

        return acc.toString();
    }

    private static void createQualifiedName(Element s, StringJoiner acc) {
        if (s != null) {
            createQualifiedName(s.getEnclosingElement(), acc);

            if (s instanceof PackageElement)
                acc.add(((PackageElement) s).getQualifiedName().toString());
            else if (s.getSimpleName().length() != 0)
                acc.add(s.getSimpleName().toString());
        }
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

    public static boolean shouldIndex(Element symbol) {
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
                return !isAnonymous(symbol);
            case CONSTRUCTOR:
                // TODO also skip generated constructors
                return !isAnonymous(symbol.getEnclosingElement());
            default:
                return false;
        }
    }

    private static boolean isAnonymous(Element symbol) {
        return symbol.getSimpleName().toString().isEmpty();
    }

    /**
     * Update a file in the index
     */
    public void update(CompilationUnitTree compilationUnit, JavacTask task) {
        Trees trees = Trees.instance(task);
        URI file = compilationUnit.getSourceFile().toUri();
        SourceFileIndex index = new SourceFileIndex();

        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitClass(ClassTree node, Void aVoid) {
                addDeclaration();

                return super.visitClass(node, aVoid);
            }

            @Override
            public Void visitMethod(MethodTree node, Void aVoid) {
                addDeclaration();

                return super.visitMethod(node, aVoid);
            }

            @Override
            public Void visitVariable(VariableTree node, Void aVoid) {
                addDeclaration();

                return super.visitVariable(node, aVoid);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void aVoid) {
                addReference();

                return super.visitMemberSelect(node, aVoid);
            }

            @Override
            public Void visitMemberReference(MemberReferenceTree node, Void aVoid) {
                addReference();

                return super.visitMemberReference(node, aVoid);
            }

            @Override
            public Void visitNewClass(NewClassTree node, Void aVoid) {
                addReference();

                return super.visitNewClass(node, aVoid);
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void aVoid) {
                addReference();

                return super.visitIdentifier(node, aVoid);
            }

            private void addDeclaration() {
                Element symbol = trees.getElement(getCurrentPath());

                if (symbol != null && onSourcePath(symbol) && shouldIndex(symbol)) {
                    symbolInformation(symbol).ifPresent(info -> {
                        String key = qualifiedName(symbol);
                        Map<String, SymbolInformation> withKind = index.declarations.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());

                        withKind.put(key, info);
                    });
                }
            }

            private void addReference() {
                Element symbol = trees.getElement(getCurrentPath());

                if (symbol != null && onSourcePath(symbol) && shouldIndex(symbol)) {
                    String key = qualifiedName(symbol);
                    Map<String, Set<Location>> withKind = index.references.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());
                    Set<Location> locations = withKind.computeIfAbsent(key, newName -> new HashSet<>());

                    findElementName(symbol, trees).ifPresent(locations::add);
                }
            }

            private Optional<SymbolInformation> symbolInformation(Element symbol) {
                return findElementName(symbol, trees).map(location -> {
                    SymbolInformation info = new SymbolInformation();

                    info.setContainerName(qualifiedName(symbol.getEnclosingElement()));
                    info.setKind(symbolInformationKind(symbol.getKind()));

                    // Constructors have name <init>, use class name instead
                    if (symbol.getKind() == ElementKind.CONSTRUCTOR)
                        info.setName(symbol.getEnclosingElement().getSimpleName().toString());
                    else
                        info.setName(symbol.getSimpleName().toString());

                    info.setLocation(location);

                    return info;
                });
            }

            private boolean onSourcePath(Element symbol) {
                return true; // TODO
            }
        }.scan(compilationUnit, null);

        sourcePath.put(file, index);
    }

    private static Optional<Location> findElementName(Element symbol, Trees trees) {
        try {
            TreePath path = trees.getPath(symbol);

            if (path == null)
                return Optional.empty();

            SourcePositions sourcePositions = trees.getSourcePositions();
            CompilationUnitTree compilationUnit = path.getCompilationUnit();
            Tree tree = path.getLeaf();
            Name name = symbol.getSimpleName();
            long startExpr = sourcePositions.getStartPosition(compilationUnit, tree);

            if (startExpr == Diagnostic.NOPOS) {
                // If default constructor, find class symbol instead
                if (symbol.getKind() == ElementKind.CONSTRUCTOR)
                    return findElementName(symbol.getEnclosingElement(), trees);
                else
                    return Optional.empty();
            }

            // If normal constructor, search for class name instead of <init>
            if (symbol.getKind() == ElementKind.CONSTRUCTOR)
                name = symbol.getEnclosingElement().getSimpleName();

            CharSequence content = compilationUnit.getSourceFile().getCharContent(false);
            int startSymbol = indexOf(content, name, (int) startExpr);

            if (startSymbol == -1)
                return Optional.empty();

            int line = (int) compilationUnit.getLineMap().getLineNumber(startSymbol);
            int column = (int) compilationUnit.getLineMap().getColumnNumber(startSymbol);

            return Optional.of(new Location(
                    compilationUnit.getSourceFile().toUri().toString(),
                    new Range(
                            new Position(line - 1, column - 1),
                            new Position(line - 1, column + name.length() - 1)
                    )
            ));
        } catch (IOException e) {
            throw new RuntimeException(e);
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

            /* Found first character, now look apply the rest of v2 */
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

    private static SymbolKind symbolInformationKind(ElementKind kind) {
        switch (kind) {
            case PACKAGE:
                return SymbolKind.Package;
            case ENUM:
            case ENUM_CONSTANT:
                return SymbolKind.Enum;
            case CLASS:
                return SymbolKind.Class;
            case ANNOTATION_TYPE:
            case INTERFACE:
                return SymbolKind.Interface;
            case FIELD:
                return SymbolKind.Property;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case TYPE_PARAMETER:
                return SymbolKind.Variable;
            case METHOD:
            case STATIC_INIT:
            case INSTANCE_INIT:
                return SymbolKind.Method;
            case CONSTRUCTOR:
                return SymbolKind.Constructor;
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                return SymbolKind.String;
        }
    }

    /**
     * Clear a file from the index when it is deleted or is about to be re-indexed
     */
    public void clear(URI file) {
        sourcePath.remove(file);
    }
}