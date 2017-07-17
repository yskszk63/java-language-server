package org.javacs;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.Modifier;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.javacs.IncrementalFileManager.ClassSig;

/**
 * An implementation of JavaFileManager that removes any .java source files where there is an up-to-date .class file
 */
class IncrementalFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Set<URI> warnedHidden = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final JavacTool javac = JavacTool.create();

    IncrementalFileManager(JavaFileManager delegate) {
        super(delegate);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        if (location == StandardLocation.SOURCE_PATH && hasUpToDateClassFile(className)) 
            return null;
        else
            return super.getJavaFileForInput(location, className, kind);
    }

    boolean hasUpToDateClassFile(String qualifiedName) {
        try {
            JavaFileObject sourceFile = super.getJavaFileForInput(StandardLocation.SOURCE_PATH, qualifiedName, JavaFileObject.Kind.SOURCE),
                           outputFile = super.getJavaFileForInput(StandardLocation.CLASS_PATH, qualifiedName, JavaFileObject.Kind.CLASS);
            long sourceModified = sourceFile == null ? 0 : sourceFile.getLastModified(),
                 outputModified = outputFile == null ? 0 : outputFile.getLastModified();
            boolean hidden = outputModified >= sourceModified || sourceSignature(qualifiedName).equals(classSignature(qualifiedName));

            if (hidden && sourceFile != null && outputFile != null && !warnedHidden.contains(sourceFile.toUri())) {
                LOG.warning("Hiding " + sourceFile.toUri() + " in favor of " + outputFile.toUri());

                warnedHidden.add(sourceFile.toUri());
            }

            if (!hidden && warnedHidden.contains(sourceFile.toUri()))
                warnedHidden.remove(sourceFile.toUri());

            return hidden;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class ClassSig {
        Set<Modifier> modifiers = Collections.emptySet();
        final List<String> typeParameters = new ArrayList<>();
        final Map<String, Set<Modifier>> fields = new HashMap<>();
        final Map<String, Set<MethodSig>> methods = new HashMap<>();

        @Override
        public boolean equals(Object candidate) {
            if (candidate instanceof ClassSig) {
                ClassSig that = (ClassSig) candidate;

                return this.modifiers.equals(that.modifiers) &&
                       this.typeParameters.equals(that.typeParameters) &&
                       this.fields.equals(that.fields) &&
                       this.methods.equals(that.methods);
            }
            else return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(modifiers, typeParameters, fields, methods);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("modifiers", modifiers)
                    .add("typeParameters", typeParameters)
                    .add("fields", fields)
                    .add("methods", methods)
                    .toString();
        }
    }

    static class MethodSig {
        Set<Modifier> modifiers = Collections.emptySet();
        List<String> typeParameters = Collections.emptyList();
        List<String> parameterTypes = Collections.emptyList();

        @Override
        public boolean equals(Object candidate) {
            if (candidate instanceof MethodSig) {
                MethodSig that = (MethodSig) candidate;

                return this.modifiers.equals(that.modifiers) &&
                       this.typeParameters.equals(that.typeParameters) &&
                       this.parameterTypes.equals(that.parameterTypes);
            }
            else return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(modifiers, typeParameters, parameterTypes);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("modifiers", modifiers)
                    .add("typeParameters", typeParameters)
                    .add("parameterTypes", parameterTypes)
                    .toString();
        }
    }

    /**
     * Signatures of all non-private methods and fields in a .java file
     */
    ClassSig sourceSignature(String qualifiedName) {
        try {
            JavaFileObject sourceFile = super.getJavaFileForInput(StandardLocation.SOURCE_PATH, qualifiedName, JavaFileObject.Kind.SOURCE);
            JavacTask task = javac.getTask(null, fileManager, __ -> {}, ImmutableList.of(), null, ImmutableList.of(sourceFile));
            CompilationUnitTree tree = task.parse().iterator().next();

            for (Tree top : tree.getTypeDecls()) {
                if (top instanceof ClassTree) {
                    ClassTree enclosingClass = (ClassTree) top;
                    
                    if (Completions.lastId(qualifiedName).contentEquals(enclosingClass.getSimpleName()))
                        return api(enclosingClass);
                }
            }

            throw new RuntimeException(qualifiedName + " not found in " + sourceFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Signatures of all non-private methods and fields in a .java file
     */
    ClassSig classSignature(String qualifiedName) {
        JavacTask task = javac.getTask(null, fileManager, __ -> {}, ImmutableList.of(), null, ImmutableList.of());
        ClassTree tree = Trees.instance(task).getTree(task.getElements().getTypeElement(qualifiedName));

        return api(tree);
    }

    private ClassSig api(ClassTree enclosingClass) {
        ClassSig result = new ClassSig();
        boolean hasNoExplicitConstructor = true;

        result.modifiers = enclosingClass.getModifiers().getFlags();

        for (Tree member : enclosingClass.getMembers()) {
            if (member instanceof VariableTree) {
                VariableTree field = (VariableTree) member;
                Set<Modifier> flags = field.getModifiers().getFlags();

                if (!flags.contains(Modifier.PRIVATE)) {
                    result.fields.put(field.getName().toString(), flags);
                }
            }
            else if (member instanceof MethodTree) {
                MethodTree method = (MethodTree) member;
                Set<Modifier> flags = method.getModifiers().getFlags();

                if (!flags.contains(Modifier.PRIVATE)) {
                    String methodName = method.getName().toString();
                    MethodSig methodApi = new MethodSig();

                    methodApi.modifiers = flags;
                    methodApi.typeParameters = Lists.transform(method.getTypeParameters(), param -> param.getName().toString());
                    methodApi.parameterTypes = Lists.transform(method.getParameters(), param -> param.getType().toString());
                    
                    result.methods.computeIfAbsent(methodName, __ -> new HashSet<>()).add(methodApi);
                }

                if (method.getName().contentEquals("<init>"))
                    hasNoExplicitConstructor = false;
            }
        }

        if (hasNoExplicitConstructor)
            result.methods.computeIfAbsent("<init>", __ -> new HashSet<>()).add(defaultConstructor());

        return result;
    }

    private MethodSig defaultConstructor() {
        MethodSig result = new MethodSig();

        result.modifiers = Collections.singleton(Modifier.PUBLIC);
        
        return result;
    }

    // NOTE this only works for regular file objects
    private String className(String packageName, JavaFileObject file) {
        String fileName = Paths.get(file.toUri()).getFileName().toString();
        String className = fileName.substring(0, fileName.indexOf('.'));

        if (packageName.isEmpty())
            return className;
        else
            return packageName + "." + className;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
