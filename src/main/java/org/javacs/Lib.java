package org.javacs;

import java.nio.file.*;
import java.util.Optional;
import java.util.logging.Logger;

class Lib {

    private static Optional<Path> cacheSrcZip;

    static Optional<Path> srcZip() {
        if (cacheSrcZip == null) {
            cacheSrcZip = findSrcZip();
        }
        return cacheSrcZip;
    }

    private static Optional<Path> findSrcZip() {
        // TODO try something else when JAVA_HOME isn't defined
        var javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            LOG.warning("Couldn't find src.zip because JAVA_HOME is not defined");
            return Optional.empty();
        }
        String[] locations = {
            "lib/src.zip", "src.zip",
        };
        for (var rel : locations) {
            var abs = Paths.get(javaHome).resolve(rel);
            if (Files.exists(abs)) {
                LOG.info("Found " + abs);
                return Optional.of(abs);
            }
        }
        LOG.warning("Couldn't find src.zip in " + javaHome);
        return Optional.empty();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
