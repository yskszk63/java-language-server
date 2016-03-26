package com.fivetran.javac.message;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JavaConfig {

    public Path rootPath = Paths.get("");

    public List<String> sourcePath = Collections.emptyList(), classPath = Collections.emptyList();

    public Optional<String> classPathFile = Optional.empty();

    public List<Dependency> dependencies;

    public Optional<String> outputDirectory = Optional.empty();
}
