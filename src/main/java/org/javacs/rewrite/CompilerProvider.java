package org.javacs.rewrite;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public interface CompilerProvider {
    Set<String> imports();

    Set<String> publicTopLevelTypes();

    Set<String> packagePrivateTopLevelTypes(String packageName);

    Path findTopLevelDeclaration(String className);

    Path[] findTypeReferences(String className);

    Path[] findMemberReferences(String className, String memberName);

    ParseTask parse(Path file);

    CompileTask compile(Path... files);

    Path NOT_FOUND = Paths.get("");
}
