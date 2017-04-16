package org.javacs;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class JavacConfig {
    public final Set<Path> sourcePath, classPath;
    public final Path outputDirectory;
    public final CompletableFuture<Set<Path>> docPath;

    public JavacConfig(Set<Path> sourcePath, Set<Path> classPath, Path outputDirectory, CompletableFuture<Set<Path>> docPath) {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
        this.outputDirectory = outputDirectory;
        this.docPath = docPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavacConfig that = (JavacConfig) o;
        return Objects.equals(sourcePath, that.sourcePath) &&
               Objects.equals(classPath, that.classPath) &&
               Objects.equals(outputDirectory, that.outputDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePath, classPath, outputDirectory);
    }
}
