package org.javacs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.javacs.Precompile.Progress;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class PrecompileTest {
    private Path workspaceRoot = Paths.get("src/test/test-project/workspace"),
        src = workspaceRoot.resolve("src"),
        outputDirectory = InferConfigTest.createOutputDir();

    @Test
    public void emitClassFile() {
        Precompile precompile = new Precompile(workspaceRoot, Collections.singleton(src), Collections.emptySet(), outputDirectory, Precompile.Progress.EMPTY);

        precompile.run();

        assertTrue("Created Test.class", Files.exists(outputDirectory.resolve("com/example/Test.class")));
    }
}