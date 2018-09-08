package org.javacs;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Logger;

import org.javacs.Urls;

class ChildFirstClassLoader extends URLClassLoader {
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
                .map(Urls::pathToUrl)
                .toArray(URL[]::new);
    }

    static ChildFirstClassLoader fromClassPath(
            String classPath, String[] packages, ClassLoader parent) {
        return new ChildFirstClassLoader(parseClassPath(classPath), packages, parent);
    }

    private ChildFirstClassLoader(URL[] urls, String[] packages, ClassLoader parent) {
        super(urls, parent);
        this.packages = packages;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        boolean fromChild = loadFromChild(name);
        Class<?> c = findLoadedClass(name);

        if (c == null && fromChild) {
            try {
                c = findClass(name);

                LOG.info("Loaded " + c + " from child class loader");

                return c;
            } catch (ClassNotFoundException e) {
                LOG.warning("Couldn't find " + name + " in child class loader");
            }
        }

        if (c == null) c = super.loadClass(name, resolve);

        if (c != null && resolve) resolveClass(c);

        return c;
    }
}
