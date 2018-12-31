package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.javacs.JavaCompilerServiceTest.contents;
import static org.junit.Assert.*;

import java.net.URI;
import org.junit.Test;

public class PrunerTest {

    @Test
    public void pruneMethods() {
        var actual = new Pruner(URI.create("/PruneMethods.java"), contents("PruneMethods.java")).prune(6, 19);
        var expected = contents("PruneMethods_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneToEndOfBlock() {
        var actual = new Pruner(URI.create("/PruneToEndOfBlock.java"), contents("PruneToEndOfBlock.java")).prune(4, 18);
        var expected = contents("PruneToEndOfBlock_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneMiddle() {
        var actual = new Pruner(URI.create("/PruneMiddle.java"), contents("PruneMiddle.java")).prune(4, 12);
        var expected = contents("PruneMiddle_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneDot() {
        var actual = new Pruner(URI.create("/PruneDot.java"), contents("PruneDot.java")).prune(3, 11);
        var expected = contents("PruneDot_erased.java");
        assertThat(actual, equalToIgnoringWhiteSpace(expected));
    }
}
