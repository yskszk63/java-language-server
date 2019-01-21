package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import javax.tools.*;
import org.junit.Before;
import org.junit.Test;

public class SimpleTest implements TaskListener, DiagnosticListener<JavaFileObject> {
    final JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
    final Path src = Paths.get("src/main/java").toAbsolutePath();
    final List<String> options = List.of("-sourcepath", src.toString(), "-verbose", "-proc:none");

    @Before
    public void setLogFormat() {
        Main.setRootFormat();
    }

    @Test
    public void standardFileManager() throws IOException {
        var fileManager = compiler.getStandardFileManager(this, null, Charset.defaultCharset());

        LOG.info("Compile once...");
        var files = fileManager.getJavaFileObjects(src.resolve("org/javacs/JavaLanguageServer.java"));
        var task = (JavacTask) compiler.getTask(null, fileManager, this, options, null, files);

        task.addTaskListener(this);
        task.analyze();

        LOG.info("Compile twice...");
        task = (JavacTask) compiler.getTask(null, fileManager, this, options, null, files);
        task.addTaskListener(this);
        task.analyze();
    }

    @Test
    public void sourceFileManager() throws IOException {
        FileStore.setWorkspaceRoots(Set.of(Paths.get(".").toAbsolutePath()));
        var fileManager = new SourceFileManager();

        LOG.info("Compile once...");
        var files =
                fileManager.getJavaFileObjectsFromFiles(
                        List.of(src.resolve("org/javacs/JavaLanguageServer.java").toFile()));
        var task = (JavacTask) compiler.getTask(null, fileManager, this, options, null, files);
        task.addTaskListener(this);
        task.analyze();
        LOG.info("...finished once");

        for (var i = 0; i < 3; i++) {
            LOG.info(String.format("Compile again %d...", i));
            task = (JavacTask) compiler.getTask(null, fileManager, this, options, null, files);
            task.addTaskListener(this);
            task.analyze();
            LOG.info(String.format("...finished %d", i));
        }
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> d) {
        LOG.warning(d.getMessage(null));
    }

    @Override
    public void started(TaskEvent e) {
        if (e.getSourceFile() == null) {
            LOG.info(String.format("...started %s", e.getKind()));
            return;
        }
        LOG.info(String.format("...started %s %s", e.getKind(), e.getSourceFile().getName()));
    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getSourceFile() == null) {
            LOG.info(String.format("...finished %s", e.getKind()));
            return;
        }
        LOG.info(String.format("...finished %s %s", e.getKind(), e.getSourceFile().getName()));
    }

    private static final Logger LOG = Logger.getLogger("main");
}
