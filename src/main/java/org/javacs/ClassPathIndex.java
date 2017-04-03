package org.javacs;

import com.google.common.reflect.ClassPath;
import sun.misc.Launcher;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Index the classpath *without* using the java compiler API.
 * The classpath can contain problematic types, for example references to classes that *aren't* present.
 * So we use reflection to find class names and constructors on the classpath.
 *
 * The isn't the only way we inspect the classpath---when completing members, for example, we use the Javac API.
 * This path is strictly for when we have to search the *entire* classpath.
 */
class ClassPathIndex {

    private final CompletableFuture<List<Class<?>>> topLevelClasses = new CompletableFuture<>();

    ClassPathIndex(Set<Path> classPath) {
        new Thread(() -> {
            try {
                ClassPath reflect = classPath(classPath);
                List<Class<?>> loadAll = new ArrayList<>();

                for (ClassPath.ClassInfo each : reflect.getTopLevelClasses())
                    tryLoad(each).ifPresent(loadAll::add);

                topLevelClasses.complete(loadAll);
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);

                topLevelClasses.completeExceptionally(e);
            }
        }, "IndexClassPath").start();
    }

    public static URLClassLoader parentClassLoader() {
        URL[] bootstrap = Launcher.getBootstrapClassPath().getURLs();

        return new URLClassLoader(bootstrap, null);
    }

    private static ClassPath classPath(Set<Path> classPath) {
        URL[] urls = classPath.stream()
                .flatMap(ClassPathIndex::url)
                .toArray(URL[]::new);

        try {
            return ClassPath.from(new URLClassLoader(urls, parentClassLoader()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<URL> url(Path path) {
        try {
            return Stream.of(path.toUri().toURL());
        } catch (MalformedURLException e) {
            LOG.warning(e.getMessage());

            return Stream.empty();
        }
    }

    private static Optional<Class<?>> tryLoad(ClassPath.ClassInfo info) {
        try {
            return Optional.of(info.load());
        } catch (LinkageError e) {
            LOG.warning(e.getMessage());

            return Optional.empty();
        }
    }

    /**
     * Find all top-level classes accessible from `fromPackage`
     */
    Stream<Class<?>> topLevelClasses(String fromPackage) {
        return topLevelClasses.join().stream()
                .filter(c -> Modifier.isPublic(c.getModifiers()) || isInPackage(c, fromPackage));
    }

    private boolean isInPackage(Class<?> c, String fromPackage) {
        return c.getPackage().getName().equals(fromPackage);
    }

    /**
     * Find all constructors in top-level classes accessible to any class in `fromPackage`
     */
    Stream<Constructor<?>> topLevelConstructors(String fromPackage) {
        return topLevelClasses(fromPackage)
                .flatMap(this::explodeConstructors);
    }

    private Stream<Constructor<?>> explodeConstructors(Class<?> c) {
        return constructors(c).filter(cons -> isAccessible(cons));
    }

    private Stream<Constructor<?>> constructors(Class<?> c) {
        try {
            return Arrays.stream(c.getConstructors());
        } catch (LinkageError e) {
            LOG.warning(e.getMessage());

            return Stream.empty();
        }
    }

    private boolean isAccessible(Constructor<?> cons) {
        return !Modifier.isPrivate(cons.getModifiers()) && !Modifier.isProtected(cons.getModifiers());
    }

    private static final Logger LOG = Logger.getLogger("main");
}
