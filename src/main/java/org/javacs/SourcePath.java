package org.javacs;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

class SourcePath {
    private final Set<Path> workspaceRoots;
    /** All .java files under workspaceRoot */
    private final Set<Path> allJavaFiles;
    /** Source roots, computed from allJavaFiles by looking at package declarations. */
    private Set<Path> sourceRoots = new HashSet<>();

    public SourcePath(Set<Path> workspaceRoots) {
        this.workspaceRoots = Collections.unmodifiableSet(workspaceRoots);
        this.sourceRoots = sourcePath(workspaceRoots);
        this.allJavaFiles = allJavaFilesInDirs(sourceRoots);
    }

    /**
     * A .java file has been created. Update allJavaFiles, but don't recalculate sourceRoots until the file is saved,
     * because the user hasn't had a change to type in a package name yet.
     */
    boolean create(Set<Path> javaFiles) {
        if (javaFiles.isEmpty()) return false;
        allJavaFiles.addAll(javaFiles);
        return checkSourceRoots();
    }

    /**
     * A .java file has been deleted. If the file is the last one in a particular source root, we should remove that
     * source root.
     */
    boolean delete(Set<Path> javaFiles) {
        if (javaFiles.isEmpty()) return false;
        allJavaFiles.removeAll(javaFiles);
        return checkSourceRoots();
    }

    /**
     * A .java file has been edited and saved. If the package declaration has been changed, we may need to recalculate
     * sourceRoots.
     */
    boolean update(Path javaFile) {
        for (var root : sourceRoots) {
            if (!javaFile.startsWith(root)) continue;
            var dir = javaFile.getParent();
            var expectedPackageName = root.relativize(dir).toString().replace('/', '.');
            var parse = Parser.parse(javaFile);
            var foundPackageName = Objects.toString(parse.getPackageName(), "");
            if (!expectedPackageName.equals(foundPackageName)) {
                LOG.info(
                        String.format(
                                "%s is in %s, which implies package %s, but now has package %s",
                                javaFile.getFileName(), root, expectedPackageName, foundPackageName));
                return checkSourceRoots();
            }
        }
        return false;
    }

    Set<Path> sourceRoots() {
        return Collections.unmodifiableSet(sourceRoots);
    }

    Set<Path> allJavaFiles() {
        return Collections.unmodifiableSet(allJavaFiles);
    }

    private boolean checkSourceRoots() {
        var newSourceRoots = sourcePath(workspaceRoots);
        if (!newSourceRoots.equals(sourceRoots)) {
            LOG.info(String.format("Source path has changed from %s to %s", sourceRoots, newSourceRoots));
            sourceRoots = newSourceRoots;
            return true;
        }
        return false;
    }

    private static Set<Path> allJavaFilesInDirs(Set<Path> dirs) {
        var all = new HashSet<Path>();
        var match = FileSystems.getDefault().getPathMatcher("glob:*.java");
        for (var dir : dirs) {
            try {
                Files.walk(dir).filter(java -> match.matches(java.getFileName())).forEach(all::add);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return all;
    }

    private static Set<Path> sourcePath(Set<Path> workspaceRoots) {
        LOG.info("Searching for source roots in " + workspaceRoots);

        var certaintyThreshold = 10;
        var sourceRoots = new HashMap<Path, Integer>();
        fileLoop:
        for (var file : allJavaFilesInDirs(workspaceRoots)) {
            // First, check if we already have a high-confidence source root containing file
            for (var root : sourceRoots.keySet()) {
                var confidence = sourceRoots.get(root);
                if (file.startsWith(root) && confidence > certaintyThreshold) {
                    continue fileLoop;
                }
            }
            // Otherwise, parse the file and look at its package declaration
            var parse = Parser.parse(file);
            var packageName = Objects.toString(parse.getPackageName(), "");
            var packagePath = packageName.replace('.', File.separatorChar);
            // If file has no package declaration, don't try to guess a source root
            // This is probably a new file that the user will add a package declaration to later
            if (packagePath.isEmpty()) {
                LOG.warning("Ignoring file with missing package declaration " + file);
                continue fileLoop;
            }
            // If path to file contradicts package declaration, give up
            var dir = file.getParent();
            if (!dir.endsWith(packagePath)) {
                LOG.warning("Java source file " + file + " is not in " + packagePath);
                continue fileLoop;
            }
            // Otherwise, use the package declaration to guess the source root
            var up = Paths.get(packagePath).getNameCount();
            var sourceRoot = dir;
            for (int i = 0; i < up; i++) {
                sourceRoot = sourceRoot.getParent();
            }
            // Increment our confidence in sourceRoot as a source root
            var count = sourceRoots.getOrDefault(sourceRoot, 0);
            sourceRoots.put(sourceRoot, count + 1);
        }
        return sourceRoots.keySet();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
