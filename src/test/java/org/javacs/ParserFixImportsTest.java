package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;
import org.junit.Test;

public class ParserFixImportsTest {
    @Test
    public void findExistingImports() throws IOException {
        var allJavaFiles =
                Files.walk(JavaCompilerServiceTest.resourcesDir())
                        .filter(f -> f.getFileName().toString().endsWith(".java"))
                        .collect(Collectors.toSet());
        assertThat(allJavaFiles, not(empty()));

        var find = Parser.existingImports(allJavaFiles);
        assertThat(find.classes, hasItem("java.util.List"));
        assertThat(find.packages, hasItem("java.util"));
    }
}
