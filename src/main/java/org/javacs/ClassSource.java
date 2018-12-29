package org.javacs;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

interface ClassSource {
    Set<String> classes();

    Optional<Class<?>> load(String className);

    static final Logger LOG = Logger.getLogger("main");
    static final Set<String> failedToLoad = new HashSet<>();

    // TODO figure this out by directly reading the class name
    // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html
    // https://hg.openjdk.java.net/jdk/jdk11/file/1ddf9a99e4ad/src/jdk.jdeps/share/classes/com/sun/tools/classfile/ClassFile.java
    default boolean isPublic(String className) {
        if (failedToLoad.contains(className)) return false;
        try {
            return load(className).map(c -> Modifier.isPublic(c.getModifiers())).orElse(false);
        } catch (Exception e) {
            LOG.warning(String.format("Failed to load %s: %s", className, e.getMessage()));
            failedToLoad.add(className);
            return false;
        }
    }

    default boolean isAccessibleFromPackage(String className, String fromPackage) {
        var packageName = Parser.mostName(className);
        return packageName.equals(fromPackage) || isPublic(className);
    }
}
