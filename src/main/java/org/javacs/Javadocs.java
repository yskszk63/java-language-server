package org.javacs;

import com.sun.javadoc.*;
import com.google.common.collect.ImmutableList;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javadoc.api.JavadocTool;

import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaFileObject.Kind;

public class Javadocs {

    /**
     * Stores known javadocs across all source paths, including dependencies
     */
    private static Javadocs global = new Javadocs(Collections.emptySet());

    /**
     * Add another source path to global()
     */
    public static void addSourcePath(Set<Path> additionalSourcePath) {
        global = global.withSourcePath(additionalSourcePath);
    }

    /**
     * A single global instance of Javadocs that incorporates all source paths
     */
    public static Javadocs global() {
        return global;
    }

    /**
     * The indexed source path, not including src.zip
     */
    private final Set<Path> userSourcePath;

    /**
     * Cache for performance reasons
     */
    private final JavacFileManager fileManager;

    /**
     * All the classes we have indexed so far
     */
    private final Map<String, RootDoc> topLevelClasses = new HashMap<>();

    private final Types types;

    private final Elements elements;

    Javadocs(Set<Path> sourcePath) {
        this.userSourcePath = sourcePath;

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

        JavacTask task = JavacTool.create().getTask(null, fileManager, Javadocs::onDiagnostic, null, null, null);

        types = task.getTypes();
        elements = task.getElements();
    }

    Javadocs withSourcePath(Set<Path> additionalSourcePath) {
        Set<Path> all = new HashSet<>();

        all.addAll(userSourcePath);
        all.addAll(additionalSourcePath);

        return new Javadocs(all);
    }

    // TODO get rid of this and use methodDoc and classDoc, which have additional variable name information
    Optional<String> docstring(Element el) {
        if (el instanceof ExecutableElement)
            return methodDoc((ExecutableElement) el).map(doc -> doc.commentText());
        else if (el instanceof TypeElement)
            return classDoc((TypeElement) el).map(doc -> doc.commentText());
        else
            return Optional.empty();
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
        String className = classElement.getQualifiedName().toString();
        RootDoc index = index(className);

        return Optional.ofNullable(index.classNamed(className));
    }

    Optional<RootDoc> update(CompilationUnitTree tree) {
        DocumentationTool.DocumentationTask task = new JavadocTool().getTask(
            null,
            fileManager,
            Javadocs::onDiagnostic,
            Javadocs.class,
            null,
            ImmutableList.of(tree.getSourceFile())
        );

        task.call();

        return getSneakyReturn();
    }

    /**
     * Get or compute the javadoc for `className`
     */
    RootDoc index(String className) {
        return topLevelClasses.computeIfAbsent(className, this::doIndex);
    }

    /**
     * Read all the Javadoc for `className`
     */
    private RootDoc doIndex(String className) {
        try {
            JavaFileObject source = fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);

            if (source == null) {
                LOG.warning("No source file for " + className);

                return EmptyRootDoc.INSTANCE;
            }
            
            LOG.info("Found " + source.toUri() + " for " + className);

            DocumentationTool.DocumentationTask task = new JavadocTool().getTask(
                    null,
                    fileManager,
                    Javadocs::onDiagnostic,
                    Javadocs.class,
                    null,
                    ImmutableList.of(source)
            );

            task.call();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        return getSneakyReturn().orElse(EmptyRootDoc.INSTANCE);
    }

    private Optional<RootDoc> getSneakyReturn() {
        RootDoc result = sneakyReturn.get();
        sneakyReturn.remove();

        if (result == null) {
            LOG.warning("index did not return a RootDoc");

            return Optional.empty();
        }
        else return Optional.of(result);
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
