package org.javacs;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a java source on the system resource path.
 */
public class FindResource {
    public static URI uri(String resourcePath) {
        if (resourcePath.startsWith("/"))
            resourcePath = resourcePath.substring(1);

        Path path = Paths.get("./src/test/resources").resolve(resourcePath).normalize();
        File file = path.toFile();

        if (!file.exists())
            throw new RuntimeException(file + " does not exist");

        return file.toURI();
    }
}
