package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Paths;
import java.util.Set;
import org.junit.Test;

public class ParserTest {
    @Test
    public void testMatchesTitleCase() {
        assertTrue(Parser.matchesTitleCase("FooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("FooBar", "fob"));
        assertTrue(Parser.matchesTitleCase("AnyPrefixFooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(Parser.matchesTitleCase("UPPERFooBar", "fb"));
        assertFalse(Parser.matchesTitleCase("Foobar", "fb"));

        assertTrue(Parser.matchesTitleCase("Prefix FooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("Prefix FooBar", "fob"));
        assertTrue(Parser.matchesTitleCase("Prefix AnyPrefixFooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("Prefix AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(Parser.matchesTitleCase("Prefix UPPERFooBar", "fb"));
        assertFalse(Parser.matchesTitleCase("Foo Bar", "fb"));
    }

    @Test
    public void searchLargeFile() {
        var largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java"));
        assertTrue(Parser.containsWordMatching(largeFile, "removeMethodBodies"));
        assertFalse(Parser.containsWordMatching(largeFile, "removeMethodBodiez"));
    }

    @Test
    public void searchSmallFile() {
        var smallFile = Paths.get(FindResource.uri("/org/javacs/example/Goto.java"));
        assertTrue(Parser.containsWordMatching(smallFile, "nonDefaultConstructor"));
        assertFalse(Parser.containsWordMatching(smallFile, "removeMethodBodies"));
    }

    @Test
    public void largeFilePossibleReference() {
        var largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java"));
        assertTrue(JavaCompilerService.containsImport("java.util.logging", "Logger", largeFile));
        assertTrue(JavaCompilerService.containsWord("removeMethodBodies", largeFile));
        assertFalse(JavaCompilerService.containsWord("removeMethodBodiez", largeFile));
    }

    @Test
    public void findAutocompleteBetweenLines() {
        var rel = Paths.get("src", "org", "javacs", "example", "AutocompleteBetweenLines.java");
        var file = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve(rel);
        assertTrue(Parser.containsWordMatching(file, "ABetweenLines"));
    }

    @Test
    public void findExistingImports() {
        var rel = Paths.get("src", "org", "javacs", "doimport");
        var dir = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve(rel);
        var existing = Parser.existingImports(Set.of(dir));
        assertThat(existing.classes, hasItems("java.util.List"));
        assertThat(existing.packages, hasItems("java.util", "java.io"));
    }
}
