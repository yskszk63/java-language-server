package org.javacs;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.logging.*;
import java.util.concurrent.*;
import javax.lang.model.element.*;
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
    private final JavacHolder compiler;

    public final BaseScanner indexer;

    /**
     * Completes when initial index is done. Useful for testing.
     */
    public final CompletableFuture<Void> initialIndexComplete = new CompletableFuture<>();

    /**
     * Symbols by class
     */
    private ConcurrentHashMap<Symbol.ClassSymbol, Set<Symbol>> index = new ConcurrentHashMap<>();
    
    public SymbolIndex(Set<Path> classPath, Set<Path> sourcePath, Path outputDirectory) {
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

        compiler.afterAnalyze(indexer);

        Thread worker = new Thread("InitialIndex") {
            List<JCTree.JCCompilationUnit> parsed = new ArrayList<>();

            @Override
            public void run() {
                // Parse each file
                sourcePath.forEach(s -> parseAll(s, parsed));

                // Compile all parsed files
                compiler.compile(parsed);

                initialIndexComplete.complete(null);
            }

            /**
             * Look for .java files and invalidate them
             */
            private void parseAll(Path path, List<JCTree.JCCompilationUnit> acc) {
                if (Files.isDirectory(path)) try {
                    Files.list(path).forEach(p -> parseAll(p, acc));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                else if (path.getFileName().toString().endsWith(".java")) {
                    LOG.info("Index " + path);

                    JavaFileObject file = compiler.fileManager.getRegularFile(path.toFile());

                    acc.add(compiler.parse(file));
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