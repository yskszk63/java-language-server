package org.javacs;

import com.google.common.base.Joiner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
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

    private static final JavacParserHolder parser = new JavacParserHolder();

    /**
     * Infer source directories by searching for .java files and examining their package declaration.
     */
    static Set<Path> workspaceSourcePath(Path workspaceRoot) {
        Function<Path, Optional<Path>> root = java -> {
            CompilationUnitTree tree = parser.parse(java.toUri(), Optional.empty()); // TODO get from JavaLanguageServer
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
                    Path truncate = dir;

                    for (int i = 0; i < up; i++)
                        truncate = truncate.getParent();

                    return Optional.of(truncate);
                }
            }
        };

        return allJavaFiles(workspaceRoot)
            .flatMap(java -> stream(root.apply(java)))
            .collect(Collectors.toSet());
    }

    static Stream<Path> allJavaFiles(Path dir) {
        PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        try {
            // TODO instead of looking at EVERY file, once you see a few files with the same source directory,
            // ignore all subsequent files in the directory
            return Files.walk(dir)
                .filter(java -> match.matches(java.getFileName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find .jar files for `externalDependencies` in local maven / gradle repository.
     */
    Set<Path> buildClassPath() {
        return externalDependencies.stream()
            .flatMap(artifact -> stream(findAnyJar(artifact, false)))
            .collect(Collectors.toSet());
    }

    /**
     * Find source .jar files for `externalDependencies` in local maven / gradle repository.
     */
    Set<Path> buildDocPath() {
        return externalDependencies.stream()
            .flatMap(artifact -> stream(findAnyJar(artifact, true)))
            .collect(Collectors.toSet());
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