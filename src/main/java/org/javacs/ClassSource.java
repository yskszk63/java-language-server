package org.javacs;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

interface ClassSource {
    Set<String> classes();

    Class<?> load(String className);

    static final Logger LOG = Logger.getLogger("main");
    static final Set<String> failedToLoad = new HashSet<>();

    default boolean isPublic(String className) {
        if (failedToLoad.contains(className)) return false;
        try {
            var c = load(className);
            return Modifier.isPublic(c.getModifiers());
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
