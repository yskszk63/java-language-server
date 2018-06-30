package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

public class DocsTest {
    @Test
    public void classDoc() {
        var sourcePath = Set.of(JavaCompilerServiceTest.resourcesDir());
        var docs = new Docs(sourcePath);
        var tree = docs.classDoc("ClassDoc");
        assertThat(tree, hasToString("A great class"));
    }

    @Test
    public void memberDoc() {
        var sourcePath = Set.of(JavaCompilerServiceTest.resourcesDir());
        var docs = new Docs(sourcePath);
        var tree = docs.memberDoc("LocalMethodDoc", "targetMethod");
        assertThat(tree, hasToString("A great method"));
    }
}
