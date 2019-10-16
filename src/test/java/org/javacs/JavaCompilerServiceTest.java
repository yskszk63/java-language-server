package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.*;

public class JavaCompilerServiceTest {
    static {
        Main.setRootFormat();
    }

    private JavaCompilerService compiler =
            new JavaCompilerService(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    static Path simpleProjectSrc() {
        return Paths.get("src/test/examples/simple-project").normalize();
    }

    @Before
    public void setWorkspaceRoot() {
        FileStore.setWorkspaceRoots(Set.of(simpleProjectSrc()));
    }

    static String contents(String resourceFile) {
        var root = JavaCompilerServiceTest.simpleProjectSrc();
        var file = root.resolve(resourceFile);
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var join = new StringJoiner("\n");
        for (var l : lines) join.add(l);
        return join.toString();
    }

    static Path resourceFile(String resourceFile) {
        var root = JavaCompilerServiceTest.simpleProjectSrc();
        return root.resolve(resourceFile);
    }

    @Test
    public void reportErrors() {
        var file = resourceFile("HasError.java");
        var files = Collections.singleton(new SourceFileObject(file));
        var diags = compiler.compileBatch(files).reportErrors();
        assertThat(diags.get(file), not(empty()));
    }

    @Test
    public void fixImports() {
        var file = resourceFile("MissingImport.java");
        var qualifiedNames = compiler.compileBatch(Set.of(new SourceFileObject(file))).fixImports(file);
        assertThat(qualifiedNames, hasItem("java.util.List"));
    }

    @Test
    public void dontImportEnum() {
        var file = resourceFile("DontImportEnum.java");
        var qualifiedNames = compiler.compileBatch(Set.of(new SourceFileObject(file))).fixImports(file);
        assertThat(qualifiedNames, contains("java.nio.file.AccessMode", "java.util.ArrayList"));
    }

    @Test
    public void matchesPartialName() {
        assertTrue(StringSearch.matchesPartialName("foobar", "foo"));
        assertFalse(StringSearch.matchesPartialName("foo", "foobar"));
    }

    @Test
    public void pruneMethods() {
        var actual = Parser.parseFile(resourceFile("PruneMethods.java")).prune(6, 19);
        var expected = contents("PruneMethods_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneToEndOfBlock() {
        var actual = Parser.parseFile(resourceFile("PruneToEndOfBlock.java")).prune(4, 18);
        var expected = contents("PruneToEndOfBlock_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneMiddle() {
        var actual = Parser.parseFile(resourceFile("PruneMiddle.java")).prune(4, 12);
        var expected = contents("PruneMiddle_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneDot() {
        var actual = Parser.parseFile(resourceFile("PruneDot.java")).prune(3, 11);
        var expected = contents("PruneDot_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneWords() {
        var actual = Parser.parseFile(resourceFile("PruneWords.java")).prune("word");
        var expected = contents("PruneWords_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }
}
