package com.fivetran.javac.message;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JavaConfig {

    public List<String> sourcePath = Collections.emptyList(), classPath = Collections.emptyList();

    public List<Dependency> dependencies;

    public Optional<String> outputDirectory = Optional.empty();
}
