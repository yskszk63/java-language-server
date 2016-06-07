package org.javacs;

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

import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.code.*;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolInformationImpl;

public class SymbolIndex {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete = new CompletableFuture<>();

    private Set<SymbolInformation> methods = new HashSet<>();
    private Set<SymbolInformation> classes = new HashSet<>();

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

    private static final int MAX_SEARCH_RESULTS = 100;

    public Stream<? extends SymbolInformation> search(String query) {

        return Stream.concat(classes.stream(), methods.stream())
                     .filter(s -> containsCharsInOrder(s.getName(), query))
                     .limit(MAX_SEARCH_RESULTS);
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

        public Indexer(JavacHolder compiler) {
            super(compiler.context);

            this.compiler = compiler;
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            super.visitClassDef(tree);

            info(tree.sym).ifPresent(classes::add);
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            info(tree.sym).ifPresent(methods::add);
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
}