package org.javacs;

import com.sun.source.doctree.DocCommentTree;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import javax.tools.*;

class Docs {

    /** File manager with source-path + platform sources, which we will use to look up individual source files */
    private final StandardJavaFileManager fileManager;

    Docs(Set<Path> sourcePath) {
        this.fileManager =
                ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

        // Compute the full source path, including src.zip for platform classes
        var allSourcePaths = new HashSet<File>();
        for (var p : sourcePath) allSourcePaths.add(p.toFile());
        // TODO src.zip

        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, allSourcePaths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JavaFileObject file(String className) {
        try {
            return fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    DocCommentTree memberDoc(String className, String memberName) {
        return DocsIndexer.memberDoc(className, memberName, file(className));
    }

    DocCommentTree classDoc(String className) {
        return DocsIndexer.classDoc(className, file(className));
    }

    private static final Logger LOG = Logger.getLogger("main");
}
