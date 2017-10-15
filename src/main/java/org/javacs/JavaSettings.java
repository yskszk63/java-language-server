package org.javacs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JavaSettings {
    public Java java = new Java();

    public static class Java {
        public List<String> classPath = new ArrayList<>();
        public List<String> externalDependencies = new ArrayList<>();
        public Optional<String> javaHome = Optional.empty();
    }
}
