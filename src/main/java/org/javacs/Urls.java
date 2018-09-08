package org.javacs;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

public final class Urls {

    private Urls() {}

    /**
     * Convert a class path element into an equivalent URL.
     *
     * @param path: The class path element
     * @return An equivalent URL
     */
    public static URL pathToUrl(String path) {
        try {
            if (isSystemPath(path))
                return Paths.get(path).toUri().toURL();
            else
                return new URL(path);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse path " + path, e);
        }
    }

    private static boolean isSystemPath(String path) {
        return path.startsWith("/") ||
            path.matches("^[a-zA-Z]:[/\\\\].*");
    }
}
