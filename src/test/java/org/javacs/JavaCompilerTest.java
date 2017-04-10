package org.javacs;

import com.google.common.collect.ImmutableList;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.api.JavacTool;
import org.junit.Test;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class JavaCompilerTest {
    private static final Logger LOG = Logger.getLogger("main");

    @Test
    public void javacTool() throws IOException {
        JavaCompiler javaCompiler = JavacTool.create();
        StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(this::reportError, null, Charset.defaultCharset());
        List<String> options = ImmutableList.of("-sourcepath", Paths.get("src/test/resources").toAbsolutePath().toString());
        List<String> classes = Collections.emptyList();
        File file = Paths.get("src/test/resources/org/javacs/example/Bad.java").toFile();
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Collections.singleton(file));
        JavacTask task = (JavacTask) javaCompiler.getTask(null, fileManager, this::reportError, options, classes, compilationUnits);

        Iterable<? extends CompilationUnitTree> parsed = task.parse();
        Iterable<? extends Element> typed = task.analyze();

        LOG.info(typed.toString());
    }

    @Test
    public void javacHolder() {
        JavacHolder javac = JavacHolder.createWithoutIndex(Collections.emptySet(), Collections.singleton(Paths.get("src/test/resources")), Paths.get("target"));
        File file = Paths.get("src/test/resources/org/javacs/example/Bad.java").toFile();
        BatchResult compile = javac.compileBatch(Collections.singletonMap(file.toURI(), Optional.empty()));
    }

    @Test
    public void incremental() {
        JavacHolder javac = JavacHolder.createWithoutIndex(Collections.emptySet(), Collections.singleton(Paths.get("src/test/resources")), Paths.get("target"));

        // Compile AutocompleteMember to a .class file
        File target = Paths.get("src/test/resources/org/javacs/example/Target.java").toFile();
        BatchResult batch = javac.compileBatch(Collections.singletonMap(target.toURI(), Optional.empty()));

        // Incremental compilation should use AutocompleteMember.class, not AutocompleteMember.java
        File dependsOnTarget = Paths.get("src/test/resources/org/javacs/example/DependsOnTarget.java").toFile();
        FocusedResult incremental = javac.compileFocused(dependsOnTarget.toURI(), Optional.empty(), 5, 27, true);

        assertThat(javac.profile().get(TaskEvent.Kind.PARSE).keySet(), hasSize(1));
    }

    private void reportError(Diagnostic<? extends JavaFileObject> error) {
        LOG.severe(error.getMessage(null));
    }
}
