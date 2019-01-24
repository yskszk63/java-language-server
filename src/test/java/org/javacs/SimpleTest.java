package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.TaskPoolProvider;
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
    final Path src = Paths.get("dummy/src").toAbsolutePath();
    final Path foo = src.resolve("foo/bar/Foo.java");
    final List<String> options = List.of("-sourcepath", src.toString(), "-verbose", "-proc:none");

    @Before
    public void setLogFormat() {
        Main.setRootFormat();
    }

    @Test
    public void freshTask() throws IOException {
        var fileManager = compiler.getStandardFileManager(this, null, Charset.defaultCharset());
        for (var i = 0; i < 2; i++) {
            LOG.info(String.format("Compile %d...", i));
            var files = fileManager.getJavaFileObjects(foo);
            var task = (JavacTask) compiler.getTask(null, fileManager, this, options, null, files);
            checkInvokeType(task);
        }
    }

    @Test
    public void taskPool() throws IOException {
        var fileManager = compiler.getStandardFileManager(this, null, Charset.defaultCharset());
        var pool = TaskPoolProvider.createTaskPool(1);
        for (var i = 0; i < 2; i++) {
            var files = fileManager.getJavaFileObjects(foo);
            LOG.info(String.format("Compile %d...", i));
            pool.getTask(
                    null,
                    fileManager,
                    this,
                    options,
                    null,
                    files,
                    task -> {
                        checkInvokeType(task);
                        return "Done!";
                    });
        }
    }

    private void checkInvokeType(JavacTask task) {
        task.addTaskListener(this);
        try {
            var root = task.parse().iterator().next();
            task.analyze();
            LOG.info("Scan " + root);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree t, Void __) {
                    if (t.getMethodSelect().toString().equals("Bar.test")) {
                        var type = Trees.instance(task).getTypeMirror(getCurrentPath());
                        LOG.info("Check " + t + ": " + type);
                        assertThat(type.toString(), equalTo("int"));
                    }
                    return null;
                }
            }.scan(root, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
