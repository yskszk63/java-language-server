package org.javacs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.Optional;

public class JavaConfigJson {
    public Set<Path> sourcePath, classPath = Collections.emptySet(), docPath = Collections.emptySet();
    public Optional<Path> classPathFile = Optional.empty(), docPathFile = Optional.empty();
    public Path outputDirectory;
}
