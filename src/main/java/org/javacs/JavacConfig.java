package org.javacs;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public class JavacConfig {
    public final Set<Path> classPath;
    public final Path outputDirectory;
    public final Set<Path> docPath;

    public JavacConfig(Set<Path> classPath, Path outputDirectory, Set<Path> docPath) {
        this.classPath = classPath;
        this.outputDirectory = outputDirectory;
        this.docPath = docPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavacConfig that = (JavacConfig) o;
        return Objects.equals(classPath, that.classPath) &&
               Objects.equals(outputDirectory, that.outputDirectory) &&
               Objects.equals(docPath, that.docPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classPath, outputDirectory, docPath);
    }
}
