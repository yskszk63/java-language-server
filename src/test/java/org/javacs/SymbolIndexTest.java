package org.javacs;

import com.sun.tools.javac.code.Symbol;
import io.typefox.lsapi.*;
import javax.tools.*;
import com.sun.tools.javac.tree.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class SymbolIndexTest {
    private static final Logger LOG = Logger.getLogger("main");

    @Test
    public void all() {
        Set<String> all = search("");

        assertThat(all, not(empty()));
    }

    @Test
    public void searchClasses() {
        Set<String> all = search("JLanguageServer");

        assertThat(all, hasItem("JavaLanguageServer"));
    }

    @Test
    public void searchMethods() {
        Set<String> all = search("gTextDocumentService");

        assertThat(all, hasItem("getTextDocumentService"));
    }

    @Test
    public void referenceConstructor() {
        JavaFileObject file = new GetResourceFileObject("/org/javacs/example/ReferenceConstructor.java");
        JCTree.JCCompilationUnit tree = compiler.parse(file);
        
        compiler.compile(tree);
        index.update(tree, compiler.context);
        
        long offset = JavaLanguageServer.findOffset(file, 2, 22);
        SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file, offset, compiler.context);

        tree.accept(visitor);

        Symbol classSymbol = visitor.found.get();
        List<Integer> references = index.references(classSymbol).map(ref -> ref.getRange().getStart().getLine()).collect(Collectors.toList());

        // Constructor reference on line 8
        assertThat(references, hasItem(8));
    }

    private Set<String> search(String query) {
        return index.search(query).map(s -> s.getName()).collect(Collectors.toSet());
    }

    private SymbolIndex index = getIndex();
    
    private static SymbolIndex getIndex() {
        try {
            Set<Path> classPath = new HashSet<>();

            for (String line : Files.readAllLines(Paths.get("classpath.txt"))) {
                for (String entry : line.split(":")) {
                    classPath.add(Paths.get(entry).toAbsolutePath());
                }
            }
            Set<Path> sourcePath = Collections.singleton(Paths.get("src/main/java").toAbsolutePath());
            Path outputDirectory = Paths.get("out").toAbsolutePath();
            SymbolIndex index = new SymbolIndex(classPath, sourcePath, outputDirectory, (paths, errs) -> {
                errs.getDiagnostics().forEach(d -> LOG.info(d.getMessage(Locale.US)));
            });

            index.initialIndexComplete.join();

            return index;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JavacHolder compiler = newCompiler();

    private static JavacHolder newCompiler() {
        return new JavacHolder(Collections.emptySet(),
                               Collections.singleton(Paths.get("src/test/resources")),
                               Paths.get("out"));
    }
}