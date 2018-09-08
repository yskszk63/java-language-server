package org.javacs;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

public final class Urls {

    public static URL of(String path) {
        try {
            if (isSystemPath(path))
                return Paths.get(path).toUri().toURL();
            else
                return new URL(path);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse path " + path, e);
        }
    }

    static boolean isSystemPath(String path) {
        return path.startsWith("/") ||
            path.matches("^[a-zA-Z]:[/\\\\].*");
    }
}