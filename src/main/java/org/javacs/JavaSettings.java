package org.javacs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JavaSettings {
    public Java java = new Java();

    public static class Java {
        public List<String> externalDependencies = new ArrayList<>();
        public Optional<Set<Path>> sourceDirectories = Optional.empty();
        public Optional<String> javaHome = Optional.empty();
    }
}