package org.javacs;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.logging.*;
import java.util.concurrent.*;
import javax.lang.model.element.*;
import java.util.function.*;
import javax.tools.*;

import java.net.URI;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.util.Context;

public class SymbolIndex {
    private static final Logger LOG = Logger.getLogger("main");

    /**
     * Private copy of compiler.
     * Compiler is very stateful, so you can't use the same compiler to do anything else concurrently.
     */
    public final JavacHolder compiler;

    public final BaseScanner indexer;

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete = new CompletableFuture<>();

    /**
     * Symbols by class
     */
    private ConcurrentHashMap<Symbol.ClassSymbol, Set<Symbol>> index = new ConcurrentHashMap<>();
    
    @FunctionalInterface
    public interface ReportDiagnostics {
        void report(Collection<Path> paths, DiagnosticCollector<JavaFileObject> diagnostics);
    }
    
    public SymbolIndex(Set<Path> classPath, 
                       Set<Path> sourcePath, 
                       Path outputDirectory, 
                       ReportDiagnostics publishDiagnostics) {
        this.compiler = new JavacHolder(classPath, sourcePath, outputDirectory);
        this.indexer = new BaseScanner(compiler.context) {
            @Override
            public void visitClassDef(JCTree.JCClassDecl tree) {
                super.visitClassDef(tree);

                if (tree.sym != null) {
                    Set<Symbol> symbols = index.computeIfAbsent(tree.sym, newClass -> new HashSet<>());

                    symbols.add(tree.sym);

                    tree.accept(new BaseScanner(compiler.context) {
                        @Override
                        public void visitMethodDef(JCTree.JCMethodDecl tree) {
                            if (tree.sym != null)
                                symbols.add(tree.sym);
                        }
                    });
                }
            }
        };
        
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
                
                // TODO destroy compiler context to recover memory 
                // We'll need to record all symbol locations up front,
                // since we'll no longer have access to source trees
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

    public Set<Symbol> search(String query) {
        Set<Symbol> found = new HashSet<>();

        index.values().forEach(index -> {
            if (found.size() < MAX_SEARCH_RESULTS) {
                index.forEach(s -> {
                    if (containsCharsInOrder(s.getSimpleName(), query))
                        found.add(s);
                });
            }
        });

        return found;
    }
    
    public Optional<SymbolLocation> locate(Symbol symbol) {
        return compiler.index.locate(symbol);
    }

    /**
     * Check if name contains all the characters of query in order.
     * For example, name 'FooBar' contains query 'FB', but not 'BF'
     */
    private boolean containsCharsInOrder(Name name, String query) {
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

    /**
     * Update an indexed file
     */
    private void update(JCTree.JCCompilationUnit tree) {
        tree.accept(indexer);
    }
}