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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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

    private final List<ClassPath.ClassInfo> topLevelClasses;

    ClassPathIndex(Set<Path> classPath) {
        topLevelClasses = classPath(classPath).getTopLevelClasses().stream()
            .sorted(ClassPathIndex::shortestName)
            .collect(Collectors.toList());

        new Thread(() -> {
            try {
                for (ClassPath.ClassInfo each : topLevelClasses)
                    tryLoad(each);
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
            }
        }, "PrefetchAllClasses").start();
    }

    private static int shortestName(ClassPath.ClassInfo left, ClassPath.ClassInfo right) {
        return Integer.compare(left.getSimpleName().length(), right.getSimpleName().length());
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

    private static Stream<Class<?>> tryLoad(ClassPath.ClassInfo info) {
        try {
            return Stream.of(info.load());
        } catch (LinkageError e) {
            LOG.warning(e.getMessage());

            return Stream.empty();
        }
    }

    /**
     * Find all top-level classes accessible from `fromPackage`
     */
    Stream<Class<?>> topLevelClasses(String partialClass, String fromPackage) {
        return topLevelClasses.stream()
                .filter(c -> Completions.containsCharactersInOrder(c.getSimpleName(), partialClass))
                .flatMap(ClassPathIndex::tryLoad)
                .filter(c -> Modifier.isPublic(c.getModifiers()) || isInPackage(c, fromPackage));
    }

    /**
     * Find all packages in parentPackage
     */
    Stream<String> packagesStartingWith(String partialPackage) {
        return topLevelClasses.stream()
                .filter(c -> c.getPackageName().startsWith(partialPackage))
                .map(c -> c.getPackageName());
    }

    Stream<Class<?>> topLevelClassesIn(String parentPackage, String partialClass, String fromPackage) {
        return topLevelClasses.stream()
                .filter(c -> c.getPackageName().equals(parentPackage) && Completions.containsCharactersInOrder(c.getSimpleName(), partialClass))
                .flatMap(ClassPathIndex::tryLoad)
                .filter(c -> Modifier.isPublic(c.getModifiers()) || isInPackage(c, fromPackage));
    }

    private boolean isInPackage(Class<?> c, String fromPackage) {
        return c.getPackage().getName().equals(fromPackage);
    }

    /**
     * Find all constructors in top-level classes accessible to any class in `fromPackage`
     */
    Stream<Constructor<?>> topLevelConstructors(String partialClass, String fromPackage) {
        return topLevelClasses(partialClass, fromPackage)
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
