package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class InferConfig {
    /**
     * Root of the workspace that is currently open in VSCode
     */
    private final Path workspaceRoot;
    /**
     * External dependencies, usually configured with java.externalDependencies
     */
    private final Collection<Artifact> externalDependencies;
    /**
     * Location of the maven repository, usually ~/.m2
     */
    private final Path mavenHome;
    /**
     * Location of the gradle cache, usually ~/.gradle
     */
    private final Path gradleHome;
    /**
     * Where to store generated .class files.
     * 
     * We don't want to interfer with the build tool, so we'll use a temporary folder managed by VSCode.
     */
    private final Path outputDirectory;

    InferConfig(Path workspaceRoot, Collection<Artifact> externalDependencies, Path mavenHome, Path gradleHome, Path outputDirectory) {
        this.workspaceRoot = workspaceRoot;
        this.externalDependencies = externalDependencies;
        this.mavenHome = mavenHome;
        this.gradleHome = gradleHome;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Infer configuration from workspace, maven / gradle repositories, ...
     */
    JavacConfig config() {
        return new JavacConfig(
            workspaceSourcePath().collect(Collectors.toSet()), 
            buildClassPath().collect(Collectors.toSet()), 
            outputDirectory, 
            CompletableFuture.completedFuture(buildDocPath().collect(Collectors.toSet()))
        );
    }

    /**
     * Infer source directories by searching for .java files and examining their package declaration.
     */
    Stream<Path> workspaceSourcePath() {
        JavacHolder parser = JavacHolder.create(Collections.emptySet(), Collections.emptySet(), Paths.get("parser-out-this-should-never-appear"));
        PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");
        Function<Path, Optional<Path>> root = java -> {
            CompilationUnitTree tree = parser.parse(java.toUri(), Optional.empty(), __ -> {}).tree;
            ExpressionTree packageTree = tree.getPackageName();
            Path dir = java.getParent();

            if (packageTree == null)
                return Optional.of(dir);
            else {
                String packagePath = packageTree.toString().replace('.', File.separatorChar);

                if (!dir.endsWith(packagePath)) {
                    LOG.warning("Java source file " + java + " is not in " + packagePath);

                    return Optional.empty();
                }
                else {
                    int up = Paths.get(packagePath).getNameCount();
                    int truncate = dir.getNameCount() - up;
                    Path src = dir.subpath(0, truncate);

                    return Optional.of(src);
                }
            }
        };

        try {
            // TODO instead of looking at EVERY file, once you see a few files with the same source directory,
            // ignore all subsequent files in the directory
            return Files.walk(workspaceRoot)
                .filter(java -> match.matches(java.getFileName()))
                .flatMap(java -> stream(root.apply(java)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find .jar files for `externalDependencies` in local maven / gradle repository.
     */
    Stream<Path> buildClassPath() {
        return externalDependencies.stream()
            .flatMap(artifact -> stream(findAnyJar(artifact, false)));
    }

    /**
     * Find source .jar files for `externalDependencies` in local maven / gradle repository.
     */
    Stream<Path> buildDocPath() {
        return externalDependencies.stream()
            .flatMap(artifact -> stream(findAnyJar(artifact, true)));
    }

    private Optional<Path> findAnyJar(Artifact artifact, boolean source) {
        Optional<Path> maven = findMavenJar(artifact, source);

        if (maven.isPresent())
            return maven;
        else
            return findGradleJar(artifact, source);
    }

    private Optional<Path> findMavenJar(Artifact artifact, boolean source) {
        Path jar = mavenHome
            .resolve("repository")
            .resolve(artifact.groupId.replace('.', File.separatorChar))
            .resolve(artifact.artifactId)
            .resolve(artifact.version)
            .resolve(fileName(artifact, source));

        if (Files.exists(jar))
            return Optional.of(jar);
        else
            return Optional.empty();
    }

    private Optional<Path> findGradleJar(Artifact artifact, boolean source) {
        // Search for caches/modules-*/files-*/groupId/artifactId/version/*/artifactId-version[-sources].jar
        Path base = gradleHome.resolve("caches");
        String pattern = "glob:" + Joiner.on(File.separatorChar).join(
            base.toString(),
            "modules-*",
            "files-*",
            artifact.groupId,
            artifact.artifactId,
            artifact.version,
            "*",
            fileName(artifact, source)
        );
        PathMatcher match = FileSystems.getDefault().getPathMatcher(pattern);

        try {
            return Files.walk(base, 7)
                .filter(match::matches)
                .findFirst();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String fileName(Artifact artifact, boolean source) {
        return artifact.artifactId + '-' + artifact.version + (source ? "-sources" : "") + ".jar";
    }

    private boolean isSourceJar(Path jar) {
        return jar.getFileName().toString().endsWith("-sources.jar");
    }

    private int compareMavenVersions(Path leftJar, Path rightJar) {
        return compareVersions(mavenVersion(leftJar), mavenVersion(rightJar));
    }

    private String[] mavenVersion(Path jar) {
        return jar.getParent().getFileName().toString().split("\\.");
    }

    private int compareVersions(String[] left, String[] right) {
        int n = Math.min(left.length, right.length);

        for (int i = 0; i < n; i++) {
            int each = left[i].compareTo(right[i]);

            if (each != 0)
                return each;
        }

        return 0;
    }

    private static <T> Stream<T> stream(Optional<T> option) {
        return option.map(Stream::of).orElseGet(Stream::empty);
    }

    private static final Logger LOG = Logger.getLogger("main");
}