package org.javacs;

import java.io.File;
import java.nio.file.*;

class Lib {
    static Path installRoot() {
        var root = Paths.get(".").toAbsolutePath();
        var p = root;
        while (p != null && !Files.exists(p.resolve("javaLsFlag.txt"))) p = p.getParent();
        if (p == null) throw new RuntimeException("Couldn't find javaLsFlag.txt in any parent of " + root);
        return p;
    }

    static final Path SRC_ZIP = installRoot().resolve("lib/src.zip");

    static final String ERROR_PRONE = findErrorProne();

    private static String findErrorProne() {
        var dataflow = installRoot().resolve("lib/error_prone/dataflow.jar").toString();
        var errorProne = installRoot().resolve("lib/error_prone/error_prone.jar").toString();
        var javacUtil = installRoot().resolve("lib/error_prone/javacutil.jar").toString();
        var jFormatString = installRoot().resolve("lib/error_prone/jFormatString.jar").toString();
        return String.join(File.pathSeparator, dataflow, errorProne, javacUtil, jFormatString);
    }
}
