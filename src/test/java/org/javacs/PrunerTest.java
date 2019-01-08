package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.javacs.JavaCompilerServiceTest.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class PrunerTest {

    @Test
    public void pruneMethods() {
        var actual = Pruner.prune(resourceUri("PruneMethods.java"), contents("PruneMethods.java"), 6, 19);
        var expected = contents("PruneMethods_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneToEndOfBlock() {
        var actual = Pruner.prune(resourceUri("PruneToEndOfBlock.java"), contents("PruneToEndOfBlock.java"), 4, 18);
        var expected = contents("PruneToEndOfBlock_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneMiddle() {
        var actual = Pruner.prune(resourceUri("PruneMiddle.java"), contents("PruneMiddle.java"), 4, 12);
        var expected = contents("PruneMiddle_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneDot() {
        var actual = Pruner.prune(resourceUri("PruneDot.java"), contents("PruneDot.java"), 3, 11);
        var expected = contents("PruneDot_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneWords() {
        var actual = Pruner.prune(resourceUri("PruneWords.java"), contents("PruneWords.java"), "word");
        var expected = contents("PruneWords_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }
}
