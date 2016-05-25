package org.javacs;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public class JavacConfig {
    public final Set<Path> sourcePath, classPath;
    public final Path outputDirectory;
    /**
     * Precedence of this type of config file, for example javaconfig.json, eclipse project file.
     * Lower precedence is stronger.
     * javaconfig.json has precedence 0, so it will always be preferred over all other types of config file.
     */
    public int precedence;

    public JavacConfig(Set<Path> sourcePath, Set<Path> classPath, Path outputDirectory, int precedence) {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
        this.outputDirectory = outputDirectory;
        this.precedence = precedence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavacConfig that = (JavacConfig) o;
        return precedence == that.precedence &&
               Objects.equals(sourcePath, that.sourcePath) &&
               Objects.equals(classPath, that.classPath) &&
               Objects.equals(outputDirectory, that.outputDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePath, classPath, outputDirectory, precedence);
    }
}
