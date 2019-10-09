package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.javacs.lsp.*;
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
    public void element() {
        var file = resourceFile("HelloWorld.java");
        var found = compiler.compileFocus(file, 3, 24).element(file, 3, 24).get();

        assertThat(found.getSimpleName(), hasToString(containsString("println")));
    }

    @Test
    public void elementWithError() {
        var file = resourceFile("HelloError.java");
        var found = compiler.compileFocus(file, 3, 19).element(file, 3, 19);

        assertThat(found, notNullValue());
    }

    @Test
    public void overloads() {
        var file = resourceFile("Overloads.java");
        var found = compiler.compileFocus(file, 3, 15).signatureHelp(file, 3, 15).get();
        var strings = found.signatures.stream().map(s -> s.label).collect(Collectors.toList());

        assertThat(strings, hasItem(containsString("print(int i)")));
        assertThat(strings, hasItem(containsString("print(String s)")));
    }

    @Test
    public void reportErrors() {
        var file = resourceFile("HasError.java");
        var files = Collections.singleton(new SourceFileObject(file));
        var diags = compiler.compileBatch(files).reportErrors();
        assertThat(diags, not(empty()));
    }

    private static List<String> errorStrings(Collection<PublishDiagnosticsParams> list) {
        var strings = new ArrayList<String>();
        for (var group : list) {
            for (var d : group.diagnostics) {
                var file = StringSearch.fileName(group.uri);
                var line = d.range.start.line;
                var msg = d.message;
                var string = String.format("%s(%d): %s", file, line, msg);
                strings.add(string);
            }
        }
        return strings;
    }

    // TODO get these back somehow
    @Test
    @Ignore
    public void errorProne() {
        var file = resourceFile("ErrorProne.java");
        var files = Collections.singleton(new SourceFileObject(file));
        var diags = compiler.compileBatch(files).reportErrors();
        var strings = errorStrings(diags);
        assertThat(strings, hasItem(containsString("ErrorProne.java(7): [CollectionIncompatibleType]")));
    }

    // TODO get these back somehow
    @Test
    @Ignore
    public void unusedVar() {
        var file = resourceFile("UnusedVar.java");
        var files = Collections.singleton(new SourceFileObject(file));
        var diags = compiler.compileBatch(files).reportErrors();
        var strings = errorStrings(diags);
        assertThat(strings, hasItem(containsString("UnusedVar.java(3): [Unused]")));
    }

    @Test
    public void localDoc() {
        var file = resourceFile("LocalMethodDoc.java");
        var invocation = compiler.compileFocus(file, 3, 21).signatureHelp(file, 3, 21).get();
        var method = invocation.signatures.get(invocation.activeSignature);
        assertThat(method.documentation.value, containsString("A great method"));
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
