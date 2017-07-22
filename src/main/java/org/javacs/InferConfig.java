package org.javacs;

import com.google.common.base.Joiner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class InferConfig {
    /** Root of the workspace that is currently open in VSCode */
    private final Path workspaceRoot;
    /** User-specified external dependencies, configured with java.externalDependencies */
    private final Collection<Artifact> externalDependencies;
    /** Location of the maven repository, usually ~/.m2 */
    private final Path mavenHome;
    /** Location of the gradle cache, usually ~/.gradle */
    private final Path gradleHome;

    InferConfig(
            Path workspaceRoot,
            Collection<Artifact> externalDependencies,
            Path mavenHome,
            Path gradleHome) {
        this.workspaceRoot = workspaceRoot;
        this.externalDependencies = externalDependencies;
        this.mavenHome = mavenHome;
        this.gradleHome = gradleHome;
    }

    private static final JavacParserHolder parser = new JavacParserHolder();

    /**
     * Infer source directories by searching for .java files and examining their package
     * declaration.
     */
    static Set<Path> workspaceSourcePath(Path workspaceRoot) {
        class Walk extends SimpleFileVisitor<Path> {
            int maxHits = 10;
            PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");
            Map<Path, Integer> sourceRoots = new HashMap<>();

            private Path currentSourceRoot(Path fileOrDir) {
                return sourceRoots
                        .keySet()
                        .stream()
                        .filter(sourceRoot -> fileOrDir.startsWith(sourceRoot))
                        .findAny()
                        .orElse(workspaceRoot);
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (sourceRoots.getOrDefault(currentSourceRoot(dir), 0) > maxHits)
                    return FileVisitResult.SKIP_SUBTREE;
                else return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (sourceRoots.getOrDefault(currentSourceRoot(file), 0) > maxHits)
                    return FileVisitResult.SKIP_SUBTREE;

                if (match.matches(file.getFileName())) {
                    checkPackageDeclaration(file)
                            .ifPresent(
                                    sourceRoot -> {
                                        LOG.info(
                                                String.format(
                                                        "%s is in source root %s",
                                                        file, sourceRoot));

                                        int hits = sourceRoots.getOrDefault(sourceRoot, 0);

                                        sourceRoots.put(sourceRoot, hits + 1);
                                    });
                }

                return FileVisitResult.CONTINUE;
            }
        }
        Walk walk = new Walk();

        try {
            Files.walkFileTree(workspaceRoot, walk);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return walk.sourceRoots.keySet();
    }

    /**
     * Parse a .java file and return it's source root based on it's package path.
     *
     * <p>For example, if the file src/com/example/Test.java has the package declaration `package
     * com.example;` then the source root is `src`.
     */
    private static Optional<Path> checkPackageDeclaration(Path java) {
        CompilationUnitTree tree =
                parser.parse(java.toUri(), Optional.empty()); // TODO get from JavaLanguageServer
        ExpressionTree packageTree = tree.getPackageName();
        Path dir = java.getParent();

        if (packageTree == null) return Optional.of(dir);
        else {
            String packagePath = packageTree.toString().replace('.', File.separatorChar);

            if (!dir.endsWith(packagePath)) {
                LOG.warning("Java source file " + java + " is not in " + packagePath);

                return Optional.empty();
            } else {
                int up = Paths.get(packagePath).getNameCount();
                Path truncate = dir;

                for (int i = 0; i < up; i++) truncate = truncate.getParent();

                return Optional.of(truncate);
            }
        }
    }

    static Stream<Path> allJavaFiles(Path dir) {
        PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        try {
            // TODO instead of looking at EVERY file, once you see a few files with the same source directory,
            // ignore all subsequent files in the directory
            return Files.walk(dir).filter(java -> match.matches(java.getFileName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find .jar files for external dependencies, for examples settings `externalDependencies` in
     * local maven / gradle repository.
     */
    Set<Path> buildClassPath() {
        // Settings `externalDependencies`
        Stream<Path> result =
                allExternalDependencies()
                        .stream()
                        .flatMap(artifact -> stream(findAnyJar(artifact, false)));

        // Bazel
        if (Files.exists(workspaceRoot.resolve("WORKSPACE"))) {
            Path bazelGenFiles = workspaceRoot.resolve("bazel-genfiles");

            if (Files.exists(bazelGenFiles) && Files.isSymbolicLink(bazelGenFiles)) {
                result = Stream.concat(result, bazelJars(bazelGenFiles));
            }
        }

        return result.collect(Collectors.toSet());
    }

    /**
     * Find directories that contain java .class files in the workspace, for example files generated
     * by maven in target/classes
     */
    Set<Path> workspaceClassPath() {
        // Bazel
        if (Files.exists(workspaceRoot.resolve("WORKSPACE"))) {
            Path bazelBin = workspaceRoot.resolve("bazel-bin");

            if (Files.exists(bazelBin) && Files.isSymbolicLink(bazelBin)) {
                return bazelOutputDirectories(bazelBin).collect(Collectors.toSet());
            }
        }

        // Maven
        try {
            return Files.walk(workspaceRoot)
                    .flatMap(this::outputDirectory)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Recognize build root files like pom.xml and return compiler output directories */
    private Stream<Path> outputDirectory(Path file) {
        if (file.getFileName().toString().equals("pom.xml")) {
            Path target = file.resolveSibling("target");

            if (Files.exists(target) && Files.isDirectory(target)) {
                return Stream.of(target.resolve("classes"), target.resolve("test-classes"));
            }
        }

        // TODO gradle

        return Stream.empty();
    }

    /**
     * Search bazel-bin for per-module output directories matching the pattern:
     *
     * <p>bazel-bin/path/to/module/_javac/rule/lib*_classes
     */
    private Stream<Path> bazelOutputDirectories(Path bazelBin) {
        try {
            Path target = Files.readSymbolicLink(bazelBin);
            PathMatcher match =
                    FileSystems.getDefault().getPathMatcher("glob:**/_javac/*/lib*_classes");

            return Files.walk(target).filter(match::matches).filter(Files::isDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Search bazel-genfiles for jars */
    private Stream<Path> bazelJars(Path bazelGenFiles) {
        try {
            Path target = Files.readSymbolicLink(bazelGenFiles);

            return Files.walk(target)
                    .filter(file -> file.getFileName().toString().endsWith(".jar"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Find source .jar files for `externalDependencies` in local maven / gradle repository. */
    Set<Path> buildDocPath() {
        return allExternalDependencies()
                .stream()
                .flatMap(artifact -> stream(findAnyJar(artifact, true)))
                .collect(Collectors.toSet());
    }

    private Optional<Path> findAnyJar(Artifact artifact, boolean source) {
        Optional<Path> maven = findMavenJar(artifact, source);

        if (maven.isPresent()) return maven;
        else return findGradleJar(artifact, source);
    }

    private Optional<Path> findMavenJar(Artifact artifact, boolean source) {
        Path jar =
                mavenHome
                        .resolve("repository")
                        .resolve(artifact.groupId.replace('.', File.separatorChar))
                        .resolve(artifact.artifactId)
                        .resolve(artifact.version)
                        .resolve(fileName(artifact, source));

        if (Files.exists(jar)) return Optional.of(jar);
        else return Optional.empty();
    }

    private Optional<Path> findGradleJar(Artifact artifact, boolean source) {
        // Search for caches/modules-*/files-*/groupId/artifactId/version/*/artifactId-version[-sources].jar
        Path base = gradleHome.resolve("caches");
        String pattern =
                "glob:"
                        + Joiner.on(File.separatorChar)
                                .join(
                                        base.toString(),
                                        "modules-*",
                                        "files-*",
                                        artifact.groupId,
                                        artifact.artifactId,
                                        artifact.version,
                                        "*",
                                        fileName(artifact, source));
        PathMatcher match = FileSystems.getDefault().getPathMatcher(pattern);

        try {
            return Files.walk(base, 7).filter(match::matches).findFirst();
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

            if (each != 0) return each;
        }

        return 0;
    }

    private static <T> Stream<T> stream(Optional<T> option) {
        return option.map(Stream::of).orElseGet(Stream::empty);
    }

    static List<Artifact> dependencyList(Path pomXml) {
        Objects.requireNonNull(pomXml, "pom.xml path is null");

        try {
            // Tell maven to output deps to a temporary file
            Path outputFile = Files.createTempFile("deps", ".txt");

            String cmd =
                    String.format(
                            "%s dependency:list -DincludeScope=test -DoutputFile=%s",
                            getMvnCommand(), outputFile);
            File workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            int result = Runtime.getRuntime().exec(cmd, null, workingDirectory).waitFor();

            if (result != 0) throw new RuntimeException("`" + cmd + "` returned " + result);

            return readDependencyList(outputFile);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Artifact> readDependencyList(Path outputFile) {
        Pattern artifact = Pattern.compile(".*:.*:.*:.*:.*");

        try (InputStream in = Files.newInputStream(outputFile)) {
            return new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .map(String::trim)
                    .filter(line -> artifact.matcher(line).matches())
                    .map(Artifact::parse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get external dependencies from this.externalDependencies if available, or try to infer them.
     */
    private Collection<Artifact> allExternalDependencies() {
        if (!externalDependencies.isEmpty()) return externalDependencies;

        // If user does not specify java.externalDependencies, look for pom.xml
        Path pomXml = workspaceRoot.resolve("pom.xml");

        if (Files.exists(pomXml)) return dependencyList(pomXml);

        return Collections.emptyList();
    }

    static String getMvnCommand() {
        String mvnCommand = "mvn";
        if (File.separatorChar == '\\') {
            mvnCommand = findExecutableOnPath("mvn.cmd");
            if (mvnCommand == null) {
                mvnCommand = findExecutableOnPath("mvn.bat");
            }
        }
        return mvnCommand;
    }

    private static String findExecutableOnPath(String name) {
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
