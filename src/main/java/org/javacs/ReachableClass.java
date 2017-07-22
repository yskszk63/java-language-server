package org.javacs;

class ReachableClass {
    final String packageName, className;
    final boolean publicClass, publicConstructor, packagePrivateConstructor, hasTypeParameters;

    ReachableClass(
            String packageName,
            String className,
            boolean publicClass,
            boolean publicConstructor,
            boolean packagePrivateConstructor,
            boolean hasTypeParameters) {
        this.packageName = packageName;
        this.className = className;
        this.publicClass = publicClass;
        this.publicConstructor = publicConstructor;
        this.packagePrivateConstructor = packagePrivateConstructor;
        this.hasTypeParameters = hasTypeParameters;
    }

    String qualifiedName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    boolean hasAccessibleConstructor(String fromPackage) {
        boolean samePackage = fromPackage.equals(packageName);

        return (publicClass && publicConstructor) || (samePackage && packagePrivateConstructor);
    }
}
