package org.javacs;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Logger;

class ChildFirstClassLoader extends ClassLoader {
    private final ClassLoader child, parent;
    private final String[] packages;
    private static final Logger LOG = Logger.getLogger("main");

    private boolean loadFromChild(String className) {
        for (String p : packages) {
            if (className.startsWith(p)) return true;
        }

        return false;
    }

    static URL[] parseClassPath(String classPath) {
        return Arrays.stream(classPath.split(File.pathSeparator))
                .map(ChildFirstClassLoader::parse)
                .toArray(URL[]::new);
    }

    private static URL parse(String urlString) {
        try {
            if (urlString.startsWith("/")) return Paths.get(urlString).toUri().toURL();
            else return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    static ChildFirstClassLoader fromClassPath(
            String classPath, String[] packages, ClassLoader parent) {
        ClassLoader child = new URLClassLoader(parseClassPath(classPath), null);
        return new ChildFirstClassLoader(child, packages, parent);
    }

    private ChildFirstClassLoader(ClassLoader child, String[] packages, ClassLoader parent) {
        this.child = child;
        this.parent = parent;
        this.packages = packages;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        boolean fromChild = loadFromChild(name);
        Class<?> c = findLoadedClass(name);

        if (c == null && fromChild) c = child.loadClass(name);

        if (c == null) c = parent.loadClass(name);

        if (resolve) resolveClass(c);

        return c;
    }
}
