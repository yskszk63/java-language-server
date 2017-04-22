package org.javacs;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class InferConfigTest {
    Path workspaceRoot = Paths.get("src/test/resources/workspace");
    Path mavenHome = Paths.get("src/test/resources/home/.m2");
    Path gradleHome = Paths.get("src/test/resources/home/.gradle");
    Path outputDirectory = createOutputDir();
    List<Artifact> externalDependencies = ImmutableList.of(new Artifact("com.external", "external-library", "1.2"));
    InferConfig both = new InferConfig(workspaceRoot, externalDependencies, mavenHome, gradleHome, outputDirectory),
        gradle = new InferConfig(workspaceRoot, externalDependencies, Paths.get("nowhere"), gradleHome, outputDirectory);

    Path createOutputDir() {
        try {
            return Files.createTempDirectory("output").toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void workspaceSourcePath() {
        assertThat(
                both.workspaceSourcePath().collect(Collectors.toSet()),
                contains(workspaceRoot.resolve("src"))
        );
    }

    @Test
    public void mavenClassPath() {
        assertThat(
                both.buildClassPath().collect(Collectors.toSet()),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2.jar"))
        );
        // v1.1 should be ignored
    }

    @Test
    public void gradleClasspath() {
        assertThat(
                gradle.buildClassPath().collect(Collectors.toSet()),
                contains(gradleHome.resolve("caches/modules-2/files-2.1/com.external/external-library/1.2/xxx/external-library-1.2.jar"))
        );
        // v1.1 should be ignored
    }

    @Test
    public void mavenDocPath() {
        assertThat(
                both.buildDocPath().collect(Collectors.toSet()),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2-sources.jar"))
        );
        // v1.1 should be ignored
    }

    @Test
    public void gradleDocPath() {
        assertThat(
                gradle.buildDocPath().collect(Collectors.toSet()),
                contains(gradleHome.resolve("caches/modules-2/files-2.1/com.external/external-library/1.2/yyy/external-library-1.2-sources.jar"))
        );
        // v1.1 should be ignored
    }
}