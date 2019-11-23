package org.javacs.rewrite;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface CompilerProvider {
    Path findTypeDeclaration(String className);

    Path[] findTypeReferences(String className);

    Path[] findMemberReferences(String className, String memberName);

    ParseTask parse(Path file);

    CompileTask compile(Path... files);

    Path NOT_FOUND = Paths.get("");
}
