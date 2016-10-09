package org.javacs;

import java.net.URI;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.logging.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.tools.*;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import io.typefox.lsapi.*;
import io.typefox.lsapi.impl.*;

/**
 * Global index of exported symbol declarations and references.
 * such as classes, methods, and fields.
 */
public class SymbolIndex {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete = new CompletableFuture<>();

    /**
     * Contains all symbol declarations and referencs in a single source file 
     */
    private static class SourceFileIndex {
        private final EnumMap<ElementKind, Map<String, SymbolInformation>> declarations = new EnumMap<>(ElementKind.class);
        private final EnumMap<ElementKind, Map<String, Set<Location>>> references = new EnumMap<>(ElementKind.class);
    }

    /**
     * Source path files, for which we support methods and classes
     */
    private Map<URI, SourceFileIndex> sourcePath = new HashMap<>();
    
    public SymbolIndex(Set<Path> classPath, 
                       Set<Path> sourcePath, 
                       Path outputDirectory) {
        JavacHolder compiler = new JavacHolder(classPath, sourcePath, outputDirectory);
        Indexer indexer = new Indexer(compiler.context);

        // Index exported declarations and references for all files on the source path
        // This may take a while, so we'll do it on an extra thread
        Thread worker = new Thread("InitialIndex") {
            List<JCTree.JCCompilationUnit> parsed = new ArrayList<>();
            List<Path> paths = new ArrayList<>();

            @Override
            public void run() {
                // Parse each file
                sourcePath.forEach(s -> parseAll(s, parsed, paths));

                // Compile all parsed files
                compiler.compile(parsed);

                parsed.forEach(p -> p.accept(indexer));
                
                // TODO minimize memory use during this process
                // Instead of doing parse-all / compile-all, 
                // queue all files, then do parse / compile on each
                // If invoked correctly, javac should avoid reparsing the same file twice
                // Then, use the same mechanism as the desugar / generate phases to remove method bodies, 
                // to reclaim memory as we go

                initialIndexComplete.complete(null);
                
                // TODO verify that compiler and all its resources get destroyed
            }

            /**
             * Look for .java files and invalidate them
             */
            private void parseAll(Path path, List<JCTree.JCCompilationUnit> trees, List<Path> paths) {
                if (Files.isDirectory(path)) try {
                    Files.list(path).forEach(p -> parseAll(p, trees, paths));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                else if (path.getFileName().toString().endsWith(".java")) {
                    LOG.info("Index " + path);

                    JavaFileObject file = compiler.fileManager.getRegularFile(path.toFile());

                    trees.add(compiler.parse(file));
                    paths.add(path);
                }
            }
        };

        worker.start();
    }

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

    private class Indexer extends BaseScanner {
        private SourceFileIndex index;

        public Indexer(Context context) {
            super(context);
        }

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            URI uri = tree.getSourceFile().toUri();

            index = new SourceFileIndex();
            sourcePath.put(uri, index);

            super.visitTopLevel(tree);

        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            super.visitClassDef(tree);

            addDeclaration(tree, tree.sym);
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            super.visitMethodDef(tree);

            addDeclaration(tree, tree.sym);
        }

        @Override
        public void visitVarDef(JCTree.JCVariableDecl tree) {
            super.visitVarDef(tree);

            addDeclaration(tree, tree.sym);
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            super.visitSelect(tree);

            addReference(tree, tree.sym);
        }

        @Override
        public void visitReference(JCTree.JCMemberReference tree) {
            super.visitReference(tree);

            addReference(tree, tree.sym);
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            addReference(tree, tree.sym);
        }

        @Override
        public void visitNewClass(JCTree.JCNewClass tree) {
            super.visitNewClass(tree);

            addReference(tree, tree.constructor);
        }

        private void addDeclaration(JCTree tree, Symbol symbol) {
            if (symbol != null && onSourcePath(symbol) && shouldIndex(symbol)) {
                String key = uniqueName(symbol);
                SymbolInformationImpl info = symbolInformation(tree, symbol, compilationUnit);
                Map<String, SymbolInformation> withKind = index.declarations.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());

                withKind.put(key, info);
            }
        }

        private void addReference(JCTree tree, Symbol symbol) {
            if (symbol != null && onSourcePath(symbol) && shouldIndex(symbol)) {
                String key = uniqueName(symbol);
                Map<String, Set<Location>> withKind = index.references.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());
                Set<Location> locations = withKind.computeIfAbsent(key, newName -> new HashSet<>());
                LocationImpl location = location(tree, compilationUnit);

                locations.add(location);
            }
        }
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

    private static SymbolInformationImpl symbolInformation(JCTree tree, Symbol symbol, JCTree.JCCompilationUnit compilationUnit) {
        LocationImpl location = location(tree, compilationUnit);
        SymbolInformationImpl info = new SymbolInformationImpl();

        info.setContainerName(symbol.getEnclosingElement().getQualifiedName().toString());
        info.setKind(symbolInformationKind(symbol.getKind()));
        
        // Constructors have name <init>, use class name instead
        if (symbol.getKind() == ElementKind.CONSTRUCTOR)
            info.setName(symbol.getEnclosingElement().getSimpleName().toString());            
        else
            info.setName(symbol.getSimpleName().toString());

        info.setLocation(location);

        return info;
    }

    public static LocationImpl location(JCTree tree, JCTree.JCCompilationUnit compilationUnit) {
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

            RangeImpl position = JavaLanguageServer.findPosition(compilationUnit.getSourceFile(),
                                                                 offset,
                                                                 end);
            LocationImpl location = new LocationImpl();

            location.setUri(compilationUnit.getSourceFile().toUri().toString());
            location.setRange(position);

            return location;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private static boolean onSourcePath(Symbol symbol) {
        return true; // TODO
    }

    private static String uniqueName(Symbol s) {
        StringJoiner acc = new StringJoiner(".");

        createUniqueName(s, acc);

        return acc.toString();
    }

    private static void createUniqueName(Symbol s, StringJoiner acc) {
        if (s != null) {
            createUniqueName(s.owner, acc);

            if (!s.getSimpleName().isEmpty())
                acc.add(s.getSimpleName().toString());
        }
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
     * Update the index when a files changes
     */
    public void update(JCTree.JCCompilationUnit tree, Context context) {
        Indexer indexer = new Indexer(context);

        tree.accept(indexer);
    }

    /**
     * Clear a file from the index when it is deleted
     */
    public void clear(URI sourceFile) {
        sourcePath.remove(sourceFile);
    }
}