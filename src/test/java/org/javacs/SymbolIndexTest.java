package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
        Set<String> all = search("ABetweenLines");

        assertThat(all, hasItem("AutocompleteBetweenLines"));
    }

    @Test
    public void searchMethods() {
        Set<String> all = search("mStatic");

        assertThat(all, hasItem("methodStatic"));
    }

    @Test
    public void referenceConstructor() {
        String path = "/org/javacs/example/ReferenceConstructor.java";
        int line = 2;
        int character = 22;

        compile(path);

        Symbol classSymbol = symbol(path, line, character);
        List<Integer> references = index.references(classSymbol)
                                        .map(ref -> ref.getRange().getStart().getLine())
                                        .collect(Collectors.toList());

        // Constructor reference on line 8
        assertThat(references, hasItem(8));
    }

    @Test
    public void symbolsInFile() {
        String path = "/org/javacs/example/AutocompleteMember.java";

        compile(path);

        List<String> all = index.allInFile(FindResource.uri(path))
                                .map(s -> s.getName())
                                .collect(Collectors.toList());

        assertThat(all, hasItems("methodStatic", "method",
                                 "methodStaticPrivate", "methodPrivate"));

        assertThat(all, hasItems("fieldStatic", "field",
                                 "fieldStaticPrivate", "fieldPrivate"));

        // TODO
        // assertThat("excludes implicit constructor", all, not(hasItems("AutocompleteMember")));
    }

    @Test
    public void explicitConstructor() {
        String path = "/org/javacs/example/ReferenceConstructor.java";

        compile(path);

        List<String> all = index.allInFile(FindResource.uri(path))
                                .map(s -> s.getName())
                                .collect(Collectors.toList());

        assertThat("includes explicit constructor", all, hasItem("ReferenceConstructor"));
    }

    private Symbol symbol(String path, int line, int character) {
        return new JavaLanguageServer(compiler)
                .findSymbol(FindResource.uri(path), line, character).orElse(null);
    }

    private JCTree.JCCompilationUnit compile(String path) {
        URI file = FindResource.uri(path);

        return compiler.compile(Collections.singletonMap(file, Optional.empty())).trees.stream().findFirst().get();
    }

    private Set<String> search(String query) {
        return index.search(query).map(s -> s.getName()).collect(Collectors.toSet());
    }

    private static Set<Path> classPath = JavaLanguageServer.buildClassPath(Paths.get("pom.xml"));
    private SymbolIndex index = getIndex();
    
    private static SymbolIndex getIndex() {
        compiler.initialIndexComplete.join();

        return compiler.index;
    }

    private static JavacHolder compiler = newCompiler();

    private static JavacHolder newCompiler() {
        return new JavacHolder(
                Collections.emptySet(),
                Collections.singleton(Paths.get("src/test/resources")),
                Paths.get("out"),
                true
        );
    }
}