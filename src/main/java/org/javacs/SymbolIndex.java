package org.javacs;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import org.eclipse.lsp4j.*;

import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Global index of exported symbol declarations and references.
 * such as classes, methods, and fields.
 */
public class SymbolIndex {

    private final Set<Path> sourcePath;

    private final Supplier<Collection<URI>> openFiles;

    private final Function<URI, Optional<String>> activeContent;

    private final JavacHolder compiler;

    private final JavacParserHolder parser;

    /**
     * Source path files, for which we support methods and classes
     */
    private final Map<URI, SourceFileIndex> sourcePathFiles = new ConcurrentHashMap<>();

    private final CompletableFuture<?> finishedInitialIndex = new CompletableFuture<>();

    SymbolIndex(Set<Path> sourcePath, Supplier<Collection<URI>> openFiles, Function<URI, Optional<String>> activeContent, JavacHolder compiler) {
        this.sourcePath = sourcePath;
        this.openFiles = openFiles;
        this.activeContent = activeContent;
        this.compiler = compiler;
        this.parser = new JavacParserHolder();

        new Thread(() -> {
            updateIndex(allJavaSources(sourcePath));

            finishedInitialIndex.complete(null);
        }, "Initial-Index").start();
    }

    private static Set<URI> allJavaSources(Set<Path> sourcePath) {
        return sourcePath.stream()
                .flatMap(InferConfig::allJavaFiles)
                .map(Path::toUri)
                .collect(Collectors.toSet());
    }

    private void updateIndex(Collection<URI> files) {
        // TODO send a progress bar to the user
        for (URI each : files) {
            if (needsUpdate(each)) {
                CompilationUnitTree tree = parser.parse(each, activeContent.apply(each));

                update(tree);
            }
        }
    }

    private boolean needsUpdate(URI file) {
        Path path = Paths.get(file);
        
        if (!sourcePathFiles.containsKey(file))
            return true;
        else try {
            Instant updated = sourcePathFiles.get(file).updated;
            Instant modified = Files.getLastModifiedTime(path).toInstant();

            return updated.isBefore(modified);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Search all indexed symbols
     */
    public Stream<SymbolInformation> search(String query) {
        finishedInitialIndex.join();

        updateIndex(openFiles.get());

        Predicate<SourceFileIndex> matchesQuery = index ->
                index != null && index.declarations.stream().anyMatch(name -> containsCharsInOrder(name, query));
        Map<URI, SourceFileIndex> candidateFiles = Maps.filterValues(sourcePathFiles, matchesQuery);

        return candidateFiles.keySet().stream()
                .flatMap(this::allInFile)
                .filter(info -> containsCharsInOrder(info.getName(), query));
    }

    /**
     * Get all declarations in an open file
     */
    public Stream<SymbolInformation> allInFile(URI source) {
        List<SymbolInformation> result = new ArrayList<>();

        visitElements(source, (task, symbol) -> {
            if (shouldIndex(symbol)) {
                findElementName(symbol, Trees.instance(task)).ifPresent(location -> {
                    SymbolInformation info = new SymbolInformation();

                    info.setContainerName(qualifiedName(symbol.getEnclosingElement()));
                    info.setKind(symbolInformationKind(symbol.getKind()));

                    // Constructors have name <init>, use class name instead
                    if (symbol.getKind() == ElementKind.CONSTRUCTOR)
                        info.setName(symbol.getEnclosingElement().getSimpleName().toString());
                    else
                        info.setName(symbol.getSimpleName().toString());

                    info.setLocation(location);

                    result.add(info);
                });
            }
        });

        return result.stream();
    }

    private void visitElements(URI source, BiConsumer<JavacTask, Element> forEach) {
        Map<URI, Optional<String>> todo = Collections.singletonMap(source, activeContent.apply(source));

        compiler.compileBatch(todo, (task, compilationUnit) -> {
            Trees trees = Trees.instance(task);

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

                private void addDeclaration() {
                    Element el = trees.getElement(getCurrentPath());

                    forEach.accept(task, el);
                }
            }.scan(compilationUnit, null);
        });
    }

    public Stream<String> allTopLevelClasses() {
        finishedInitialIndex.join();

        return sourcePathFiles.values().stream().flatMap(index -> index.topLevelClasses.stream());
    }

    /**
     * Find all references to a symbol
     */
    public Stream<Location> references(Element symbol) {
        finishedInitialIndex.join();

        updateIndex(openFiles.get());

        String name = symbol.getSimpleName().toString();
        Map<URI, SourceFileIndex> hasName = Maps.filterValues(sourcePathFiles, index -> index.references.contains(name));

        return findReferences(hasName.keySet(), symbol).stream();
    }

    /**
     * Find a symbol in its file.
     * 
     * It's possible that `symbol` comes from a .class file where the corresponding .java file was not visited during incremental compilation.
     * In order to be sure we have access to the source positions, we will recompile the .java file where `symbol` was declared.
     */
    public Optional<Location> find(Element symbol) {
        finishedInitialIndex.join();

        updateIndex(openFiles.get());

        return findFile(symbol).flatMap(file -> findIn(symbol, file));
    }

    private Optional<Location> findIn(Element symbol, URI file) {
        List<Location> result = new ArrayList<>();

        visitElements(file, (task, found) -> {
            if (sameSymbol(symbol, found)) {
                findElementName(found, Trees.instance(task)).ifPresent(result::add);
            }
        });

        if (!result.isEmpty())
            return Optional.of(result.get(0));
        else
            return Optional.empty();
    }

    private Optional<URI> findFile(Element symbol) {
        return topLevelClass(symbol).flatMap(this::findDeclaringFile);
    }

    private Optional<URI> findDeclaringFile(TypeElement topLevelClass) {
        String name = topLevelClass.getQualifiedName().toString();

        return Maps.filterValues(sourcePathFiles, index -> index.topLevelClasses.contains(name)).keySet().stream().findFirst();
    }

    private Optional<TypeElement> topLevelClass(Element symbol) {
        TypeElement result = null;

        while (symbol != null) {
            if (symbol instanceof TypeElement)
                result = (TypeElement) symbol;

            symbol = symbol.getEnclosingElement();
        }

        return Optional.ofNullable(result);
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
        if (symbol == null)
            return false;
            
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
    private void update(CompilationUnitTree compilationUnit) {
        URI file = compilationUnit.getSourceFile().toUri();
        SourceFileIndex index = new SourceFileIndex();

        new TreePathScanner<Void, Void>() {
            int classDepth = 0;

            @Override
            public Void visitClass(ClassTree node, Void aVoid) {
                // If this is a top-level class, add qualified name to special topLevelClasses index
                if (classDepth == 0)
                    index.topLevelClasses.add(qualifiedName(node, compilationUnit));

                // Add simple name to declarations
                index.declarations.add(node.getSimpleName().toString());

                // Recurse, but remember that anything inside isn't a top-level class
                classDepth++;
                super.visitClass(node, aVoid);
                classDepth--;

                return null;
            }

            @Override
            public Void visitMethod(MethodTree node, Void aVoid) {
                index.declarations.add(node.getName().toString());

                return super.visitMethod(node, aVoid);
            }

            @Override
            public Void visitVariable(VariableTree node, Void aVoid) {
                if (getCurrentPath().getParentPath().getLeaf().getKind() == Tree.Kind.CLASS)
                    index.declarations.add(node.getName().toString());

                return super.visitVariable(node, aVoid);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void aVoid) {
                index.references.add(node.getIdentifier().toString());

                return super.visitMemberSelect(node, aVoid);
            }

            @Override
            public Void visitMemberReference(MemberReferenceTree node, Void aVoid) {
                index.references.add(node.getName().toString());

                return super.visitMemberReference(node, aVoid);
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void aVoid) {
                index.references.add(node.getName().toString());

                return super.visitIdentifier(node, aVoid);
            }
        }.scan(compilationUnit, null);

        sourcePathFiles.put(file, index);
    }

    private String qualifiedName(ClassTree node, CompilationUnitTree compilationUnit) {
        String packageName = Objects.toString(compilationUnit.getPackageName(), "");
        Name className = node.getSimpleName();
        StringJoiner qualifiedName = new StringJoiner(".");

        if (!packageName.isEmpty())
            qualifiedName.add(packageName);
        qualifiedName.add(className);
        return qualifiedName.toString();
    }

    private List<Location> findReferences(Collection<URI> files, Element target) {
        List<Location> found = new ArrayList<>();
        Map<URI, Optional<String>> todo = files.stream().collect(Collectors.toMap(uri -> uri, activeContent));

        compiler.compileBatch(todo, (task, compilationUnit) -> {
            Trees trees = Trees.instance(task);

            new TreePathScanner<Void, Void>() {
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

                private void addReference() {
                    Element symbol = trees.getElement(getCurrentPath());

                    if (sameSymbol(target, symbol))
                        findPath(getCurrentPath(), trees).ifPresent(found::add);
                }
            }.scan(compilationUnit, null);
        });

        return found;
    }

    private static boolean sameSymbol(Element target, Element symbol) {
        return symbol != null && target != null &&
            toStringEquals(symbol.getEnclosingElement(), target.getEnclosingElement()) &&
            toStringEquals(symbol, target);
    }

    private static boolean toStringEquals(Object left, Object right) {
        return Objects.equals(Objects.toString(left, ""), Objects.toString(right, ""));
    }

    private static Optional<Location> findPath(TreePath path, Trees trees) {
        CompilationUnitTree compilationUnit = path.getCompilationUnit();
        long start = trees.getSourcePositions().getStartPosition(compilationUnit, path.getLeaf());
        long end = trees.getSourcePositions().getEndPosition(compilationUnit, path.getLeaf());

        if (start == Diagnostic.NOPOS)
            return Optional.empty();

        if (end == Diagnostic.NOPOS)
            end = start;
        
        int startLine = (int) compilationUnit.getLineMap().getLineNumber(start);
        int startColumn = (int) compilationUnit.getLineMap().getColumnNumber(start);
        int endLine = (int) compilationUnit.getLineMap().getLineNumber(end);
        int endColumn = (int) compilationUnit.getLineMap().getColumnNumber(end);

        return Optional.of(new Location(
                compilationUnit.getSourceFile().toUri().toString(),
                new Range(
                        new Position(startLine - 1, startColumn - 1),
                        new Position(endLine - 1, endColumn - 1)
                )
        ));
    }

    /**
     * Find a more accurate position for symbol than the one offered by javac, by searching for its name.
     * 
     * `trees` must be from a compilation that includes the declaration of `symbol`; incremental compilation may not always have this.
     */
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

    private static final Logger LOG = Logger.getLogger("main");

}