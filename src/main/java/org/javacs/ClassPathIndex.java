package org.javacs;

import com.google.common.reflect.ClassPath;
import com.sun.source.util.JavacTask;

import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ClassPathIndex {

    private final List<TypeElement> publicTopLevelClasses;

    public ClassPathIndex(JavacTask task, Set<Path> classPath) {
        ClassPath reflect = classPath(classPath);

        this.publicTopLevelClasses = reflect.getTopLevelClasses()
                .stream()
                .filter(ClassPathIndex::isPublic)
                .flatMap(info -> classElement(info, task))
                .collect(Collectors.toList());
    }

    private static Stream<TypeElement> classElement(ClassPath.ClassInfo info, JavacTask task) {
        try {
            TypeElement candidate = task.getElements().getTypeElement(info.getName());

            if (candidate != null)
                return Stream.of(candidate);
            else
                return Stream.empty();
        } catch (Exception e) {
            LOG.warning(e.getMessage());

            return Stream.empty();
        }
    }

    private static ClassPath classPath(Set<Path> classPath) {
        URL[] urls = classPath.stream()
                .flatMap(ClassPathIndex::url)
                .toArray(URL[]::new);

        try {
            return ClassPath.from(new URLClassLoader(urls));
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

    private static boolean isPublic(ClassPath.ClassInfo info) {
        try {
            int modifiers = info.load().getModifiers();

            return Modifier.isPublic(modifiers);
        } catch (LinkageError e) {
            LOG.warning(e.getMessage());

            return false;
        }
    }

    Stream<TypeElement> publicTopLevelClasses() {
        return publicTopLevelClasses.stream();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
