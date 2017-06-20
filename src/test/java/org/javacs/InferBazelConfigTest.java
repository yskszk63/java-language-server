package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class InferBazelConfigTest {

    private Path bazelWorkspace = Paths.get("src/test/test-project/bazel-workspace"),
                 bazelTemp = Paths.get("src/test/test-project/bazel-temp");
    private InferConfig bazel = new InferConfig(bazelWorkspace, Collections.emptyList(), Paths.get("nowhere"), Paths.get("nowhere"));
    private Path bazelBin = bazelWorkspace.resolve("bazel-bin"),
                 bazelBinTarget = bazelTemp.resolve("xyz/execroot/test/bazel-out/local-fastbuild/bin").toAbsolutePath();

    @Before
    public void createBazelBinLink() throws IOException {
        assertTrue(Files.exists(bazelBinTarget));

        Files.createSymbolicLink(bazelBin, bazelBinTarget);
    }

    @After
    public void deleteBazelBinLink() throws IOException {
        Files.deleteIfExists(bazelBin);
    }

    @Test
    public void bazelWorkspaceClassPath() {
        assertThat(
            bazel.workspaceClassPath(),
            hasItem(bazelBinTarget.resolve("module/_javac/main/libmain_classes"))
        );
    }
}