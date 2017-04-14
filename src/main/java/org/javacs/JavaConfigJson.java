package org.javacs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.Optional;

public class JavaConfigJson {
    public Set<Path> sourcePath;
    public Optional<Path> classPathFile = Optional.empty();
    public Set<Path> classPath = Collections.emptySet();
    public Path outputDirectory;
}
