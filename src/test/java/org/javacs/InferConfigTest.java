package org.javacs;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

public class InferConfigTest {
    private Path workspaceRoot = Paths.get("src/test/test-project/workspace");
    private Path mavenHome = Paths.get("src/test/test-project/home/.m2");
    private Path gradleHome = Paths.get("src/test/test-project/home/.gradle");
    private Path outputDirectory = createOutputDir();
    private List<Artifact> externalDependencies = ImmutableList.of(new Artifact("com.external", "external-library", "1.2"));
    private InferConfig both = new InferConfig(workspaceRoot, externalDependencies, mavenHome, gradleHome, outputDirectory),
        gradle = new InferConfig(workspaceRoot, externalDependencies, Paths.get("nowhere"), gradleHome, outputDirectory);

    private Path createOutputDir() {
        try {
            return Files.createTempDirectory("output").toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void workspaceSourcePath() {
        assertThat(
                InferConfig.workspaceSourcePath(workspaceRoot),
                contains(workspaceRoot.resolve("src"))
        );
    }

    @Test
    public void mavenClassPath() {
        assertThat(
                both.buildClassPath(),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2.jar"))
        );
        // v1.1 should be ignored
    }

    @Test
    public void gradleClasspath() {
        assertThat(
                gradle.buildClassPath(),
                contains(gradleHome.resolve("caches/modules-2/files-2.1/com.external/external-library/1.2/xxx/external-library-1.2.jar"))
        );
        // v1.1 should be ignored
    }

    @Test
    public void mavenDocPath() {
        assertThat(
                both.buildDocPath(),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2-sources.jar"))
        );
        // v1.1 should be ignored
    }

    @Test
    public void gradleDocPath() {
        assertThat(
                gradle.buildDocPath(),
                contains(gradleHome.resolve("caches/modules-2/files-2.1/com.external/external-library/1.2/yyy/external-library-1.2-sources.jar"))
        );
        // v1.1 should be ignored
    }
}