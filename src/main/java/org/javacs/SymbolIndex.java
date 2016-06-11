package org.javacs;

import java.net.URI;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.logging.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.tools.*;

import javax.tools.JavaFileObject;

import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.util.Name;
import io.typefox.lsapi.*;

public class SymbolIndex {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete = new CompletableFuture<>();

    private static class SourceFileIndex {
        private final Set<SymbolInformation> methods = new HashSet<>();
        private final Set<SymbolInformation> classes = new HashSet<>();
        private final EnumMap<ElementKind, Map<String, Set<Location>>> references = new EnumMap<>(ElementKind.class);
    }

    private Map<URI, SourceFileIndex> files = new HashMap<>();

    @FunctionalInterface
    public interface ReportDiagnostics {
        void report(Collection<Path> paths, DiagnosticCollector<JavaFileObject> diagnostics);
    }
    
    public SymbolIndex(Set<Path> classPath, 
                       Set<Path> sourcePath, 
                       Path outputDirectory, 
                       ReportDiagnostics publishDiagnostics) {
        JavacHolder compiler = new JavacHolder(classPath, sourcePath, outputDirectory);
        Indexer indexer = new Indexer(compiler);
        
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();

        compiler.afterAnalyze(indexer);
        compiler.onError(errors);

        Thread worker = new Thread("InitialIndex") {
            List<JCTree.JCCompilationUnit> parsed = new ArrayList<>();
            List<Path> paths = new ArrayList<>();

            @Override
            public void run() {
                // Parse each file
                sourcePath.forEach(s -> parseAll(s, parsed, paths));

                // Compile all parsed files
                compiler.compile(parsed);
                
                // TODO minimize memory use during this process
                // Instead of doing parse-all / compile-all, 
                // queue all files, then do parse / compile on each
                // If invoked correctly, javac should avoid reparsing the same file twice
                // Then, use the same mechanism as the desugar / generate phases to remove method bodies, 
                // to reclaim memory as we go
                
                // Report diagnostics to language server
                publishDiagnostics.report(paths, errors);
                
                // Stop recording diagnostics
                compiler.onError(err -> {});

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

    public Stream<? extends SymbolInformation> search(String query) {
        Stream<SymbolInformation> classes = files.values().stream().flatMap(f -> f.classes.stream());
        Stream<SymbolInformation> methods = files.values().stream().flatMap(f -> f.methods.stream());

        return Stream.concat(classes, methods)
                     .filter(s -> containsCharsInOrder(s.getName(), query));
    }

    public Stream<? extends Location> references(Symbol symbol) {
        String key = uniqueName(symbol);

        return files.values().stream().flatMap(f -> {
            Map<String, Set<Location>> bySymbol = f.references.getOrDefault(symbol.getKind(), Collections.emptyMap());
            Set<Location> locations = bySymbol.getOrDefault(key, Collections.emptySet());

            return locations.stream();
        });
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
        private JavacHolder compiler;
        private SourceFileIndex index = new SourceFileIndex();

        public Indexer(JavacHolder compiler) {
            super(compiler.context);

            this.compiler = compiler;
        }

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            super.visitTopLevel(tree);

            URI uri = tree.getSourceFile().toUri();

            files.put(uri, index);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            super.visitClassDef(tree);

            info(tree.sym).ifPresent(index.classes::add);
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            super.visitMethodDef(tree);

            info(tree.sym).ifPresent(index.methods::add);
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
            Symbol symbol = tree.sym;

            addReference(tree, symbol);
        }

        private void addReference(JCTree tree, Symbol symbol) {
            if (symbol != null) {
                ElementKind kind = symbol.getKind();

                switch (kind) {
                    case ENUM:
                    case CLASS:
                    case ANNOTATION_TYPE:
                    case INTERFACE:
                    case ENUM_CONSTANT:
                    case FIELD:
                    case METHOD:
                    case CONSTRUCTOR:
                        if (onSourcePath(symbol)) {
                            String key = uniqueName(symbol);
                            Map<String, Set<Location>> bySymbol = index.references.computeIfAbsent(kind, newKind -> new HashMap<>());
                            Set<Location> locations = bySymbol.computeIfAbsent(key, newName -> new HashSet<>());
                            LocationImpl location = location(tree);

                            locations.add(location);
                        }
                }
            }
        }

        private LocationImpl location(JCTree tree) {
            RangeImpl position = JavaLanguageServer.findPosition(compilationUnit.getSourceFile(),
                                                                 tree.getStartPosition(),
                                                                 tree.getEndPosition(null));
            LocationImpl location = new LocationImpl();

            location.setUri(compilationUnit.getSourceFile().toUri().toString());
            location.setRange(position);
            return location;
        }

        private Optional<SymbolInformationImpl> info(Symbol symbol) {
            return Optional.ofNullable(symbol)
                           .flatMap(compiler.index::locate)
                           .map(location -> {
                SymbolInformationImpl info = new SymbolInformationImpl();

                info.setLocation(location.location());
                info.setContainer(symbol.getEnclosingElement().getQualifiedName().toString());
                info.setKind(symbolInformationKind(symbol.getKind()));
                info.setName(symbol.getSimpleName().toString());

                return info;
            });
        }
    }

    private boolean onSourcePath(Symbol symbol) {
        return true; // TODO
    }

    private String uniqueName(Symbol s) {
        StringJoiner acc = new StringJoiner(".");

        createUniqueName(s, acc);

        return acc.toString();
    }

    private void createUniqueName(Symbol s, StringJoiner acc) {
        if (s != null) {
            createUniqueName(s.owner, acc);

            if (!s.getSimpleName().isEmpty())
                acc.add(s.getSimpleName().toString());
        }
    }

    private static int symbolInformationKind(ElementKind kind) {
        switch (kind) {
            case PACKAGE:
                return SymbolInformation.KIND_PACKAGE;
            case ENUM:
            case ENUM_CONSTANT:
                return SymbolInformation.KIND_ENUM;
            case CLASS:
                return SymbolInformation.KIND_CLASS;
            case ANNOTATION_TYPE:
            case INTERFACE:
                return SymbolInformation.KIND_INTERFACE;
            case FIELD:
                return SymbolInformation.KIND_PROPERTY;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case TYPE_PARAMETER:
                return SymbolInformation.KIND_VARIABLE;
            case METHOD:
            case STATIC_INIT:
            case INSTANCE_INIT:
                return SymbolInformation.KIND_METHOD;
            case CONSTRUCTOR:
                return SymbolInformation.KIND_CONSTRUCTOR;
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                return SymbolInformation.KIND_STRING;
        }
    }

    /**
     * Scanner that will add class and method symbols to the index
     */
    public BaseScanner indexer(JavacHolder compiler) {
        return new Indexer(compiler);
    }

    public void clear(URI sourceFile) {
        files.remove(sourceFile);
    }
}