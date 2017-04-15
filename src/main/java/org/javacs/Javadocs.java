package org.javacs;

import com.sun.javadoc.ClassDoc;
import com.google.common.collect.ImmutableList;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javadoc.api.JavadocTool;
import org.eclipse.lsp4j.DiagnosticSeverity;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Javadocs {
    /**
     * Cache for performance reasons
     */
    private final JavacFileManager fileManager;

    /**
     * All the packages we have indexed so far
     */
    private final Map<String, RootDoc> packages = new HashMap<>();

    private final Types types;

    Javadocs(Set<Path> sourcePath) {
        Set<File> allSourcePaths = new HashSet<>();

        sourcePath.stream()
                .map(Path::toFile)
                .forEach(allSourcePaths::add);
        findSrcZip().ifPresent(allSourcePaths::add);

        fileManager = JavacTool.create().getStandardFileManager(Javadocs::onDiagnostic, null, null);

        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, allSourcePaths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        types = JavacTool.create().getTask(null, fileManager, Javadocs::onDiagnostic, null, null, null).getTypes();
    }

    Optional<MethodDoc> methodDoc(ExecutableElement method) {
        TypeElement classElement = (TypeElement) method.getEnclosingElement();

        return classDoc(classElement).flatMap(classDoc -> doMethodDoc(classDoc, method));
    }

    private Optional<MethodDoc> doMethodDoc(ClassDoc classDoc, ExecutableElement method) {
        for (MethodDoc each : classDoc.methods(false)) {
            if (methodMatches(method, each))
                return Optional.of(each);
        }

        return Optional.empty();
    }

    private boolean methodMatches(ExecutableElement method, MethodDoc doc) {
        return method.getSimpleName().toString().equals(doc.name()) &&
            parametersMatch(method.getParameters(), doc.parameters());
    }

    private boolean parametersMatch(List<? extends VariableElement> vars, Parameter[] docs) {
        if (vars.size() != docs.length) 
            return false;
        
        for (int i = 0; i < vars.size(); i++) {
            if (!parameterMatches(vars.get(i), docs[i]))
                return false;
        }

        return true;
    }

    private boolean parameterMatches(VariableElement var, Parameter doc) {
        String varString = types.erasure(var.asType()).toString();
        String docString = doc.type().toString();

        return varString.equals(docString);
    }

    Optional<ClassDoc> classDoc(TypeElement classElement) {
        PackageElement enclosingPackage = (PackageElement) classElement.getEnclosingElement();
        String packageName = enclosingPackage.getQualifiedName().toString();
        RootDoc packageDoc = index(packageName);
        String className = classElement.getQualifiedName().toString();
        
        return Optional.ofNullable(packageDoc.classNamed(className));
    }

    /**
     * Get or compute the javadoc for `packageName`
     */
    RootDoc index(String packageName) {
        return packages.computeIfAbsent(packageName, this::doIndex);
    }

    /**
     * Read all the Javadoc for `packageName`
     */
    private RootDoc doIndex(String packageName) {
        DocumentationTool.DocumentationTask task = new JavadocTool().getTask(
                null,
                fileManager,
                Javadocs::onDiagnostic,
                Javadocs.class,
                ImmutableList.of(packageName),
                null
        );

        task.call();

        RootDoc result = sneakyReturn.get();
        sneakyReturn.remove();

        if (result == null)
            throw new RuntimeException("index(" + packageName + ") did not return a RootDoc");
        else
            return result;
    }

    /**
     * start(RootDoc) uses this to return its result to doIndex(...)
     */
    private static ThreadLocal<RootDoc> sneakyReturn = new ThreadLocal<>();

    /**
     * Called by the javadoc tool
     *
     * {@link Doclet}
     */
    public static boolean start(RootDoc root) {
        sneakyReturn.set(root);

        return true;
    }

    /**
     * Find the copy of src.zip that comes with the system-installed JDK
     */
    private static Optional<File> findSrcZip() {
        Path path = Paths.get(System.getProperty("java.home"));

        if (path.endsWith("jre"))
            path = path.getParent();

        path = path.resolve("src.zip");

        File file = path.toFile();

        if (file.exists())
            return Optional.of(file);
        else
            return Optional.empty();
    }

    private static void onDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        Level level = level(diagnostic.getKind());
        String message = diagnostic.getMessage(null);

        LOG.log(level, message);
    }

    private static Level level(Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return Level.SEVERE;
            case WARNING:
            case MANDATORY_WARNING:
                return Level.WARNING;
            case NOTE:
                return Level.INFO;
            case OTHER:
            default:
                return Level.FINE;
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
