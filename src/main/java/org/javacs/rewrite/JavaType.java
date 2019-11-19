package org.javacs.rewrite;

/** JavaType represents a potentially parameterized named type. */
class JavaType {
    final String name;
    final JavaType[] parameters;

    JavaType(String name, JavaType[] parameters) {
        this.name = name;
        this.parameters = parameters;
    }
}
