package org.javacs;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import com.google.common.collect.ImmutableList;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javadoc.api.JavadocTool;

public class Javadocs {
    /**
     * Cache for performance reasons
     */
    private final JavacFileManager fileManager;

    /**
     * All the packages we have indexed so far
     */
    private final Map<String, RootDoc> packages = new HashMap<>();

    Javadocs(Set<Path> sourcePath) {
        Set<File> allSourcePaths = new HashSet<>();

        sourcePath.stream()
                .map(Path::toFile)
                .forEach(allSourcePaths::add);
        findSrcZip().ifPresent(allSourcePaths::add);

        fileManager = JavacTool.create().getStandardFileManager(Javadocs::onError, null, null);

        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, allSourcePaths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get or compute the javadoc for `packageName`
     */
    RootDoc index(String packageName) {
        return packages.computeIfAbsent(packageName, this::doIndex);
    }

    /**
     * Read all the Javadoc for `packageName`
     */
    private RootDoc doIndex(String packageName) {
        DocumentationTool.DocumentationTask task = new JavadocTool().getTask(
                null,
                fileManager,
                Javadocs::onError,
                Javadocs.class,
                ImmutableList.of(packageName),
                null
        );

        task.call();

        RootDoc result = sneakyReturn.get();
        sneakyReturn.remove();

        if (result == null)
            throw new RuntimeException("index(" + packageName + ") did not return a RootDoc");
        else
            return result;
    }

    /**
     * start(RootDoc) uses this to return its result to doIndex(...)
     */
    private static ThreadLocal<RootDoc> sneakyReturn = new ThreadLocal<>();

    /**
     * Called by the javadoc tool
     *
     * {@link Doclet}
     */
    public static boolean start(RootDoc root) {
        sneakyReturn.set(root);

        return true;
    }

    /**
     * Find the copy of src.zip that comes with the system-installed JDK
     */
    private static Optional<File> findSrcZip() {
        Path path = Paths.get(System.getProperty("java.home"));

        if (path.endsWith("jre"))
            path = path.getParent();

        path = path.resolve("src.zip");

        File file = path.toFile();

        if (file.exists())
            return Optional.of(file);
        else
            return Optional.empty();
    }

    private static void onError(Diagnostic<? extends JavaFileObject> diagnostic) {
        LOG.warning(diagnostic.getMessage(null));
    }

    private static final Logger LOG = Logger.getLogger("main");
}
