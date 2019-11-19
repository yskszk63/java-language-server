package org.javacs.rewrite;

import com.sun.source.util.JavacTask;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface CompilerProvider {
    Path findClass(String className);

    Path[] findReferences(String className);

    JavacTask parse(Path file);

    JavacTask compile(Path[] files);

    Path NOT_FOUND = Paths.get("");
}
