package org.javacs;

import com.sun.source.doctree.DocCommentTree;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.*;
import jdk.javadoc.doclet.*;

public class DocsIndexer implements Doclet {

    private static String targetClass, targetMember;
    private static DocCommentTree result;

    @Override
    public String getName() {
        return "Indexer";
    }

    @Override
    public Set<Doclet.Option> getSupportedOptions() {
        return Set.of();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_10;
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
        // Nothing to do
    }

    @Override
    public boolean run(DocletEnvironment env) {
        Objects.requireNonNull(targetClass, "DocIndexer.targetClass has not been set");

        var els = env.getSpecifiedElements();
        if (els.isEmpty()) throw new RuntimeException("No specified elements");
        var docs = env.getDocTrees();
        var elements = env.getElementUtils();
        for (var e : els) {
            if (e instanceof TypeElement) {
                var t = (TypeElement) e;
                if (t.getQualifiedName().contentEquals(targetClass)) {
                    if (targetMember == null) {
                        result = docs.getDocCommentTree(t);
                    } else {
                        for (var member : elements.getAllMembers(t)) {
                            if (member.getSimpleName().contentEquals(targetMember)) {
                                result = docs.getDocCommentTree(member);
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Empty file manager we pass to javadoc to prevent it from roaming all over the place */
    private static final StandardJavaFileManager emptyFileManager =
            ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

    private static DocumentationTool.DocumentationTask task(JavaFileObject file, String className) {
        var tool = ToolProvider.getSystemDocumentationTool();
        return tool.getTask(
                null,
                emptyFileManager,
                err -> LOG.severe(err.getMessage(null)),
                DocsIndexer.class,
                List.of("--ignore-source-errors", "-Xclasses", className),
                List.of(file));
    }

    public static DocCommentTree classDoc(String className, JavaFileObject file) {
        Objects.requireNonNull(file, "file is null");

        try {
            targetClass = className;
            var task = task(file, className);
            if (!task.call()) throw new RuntimeException("Documentation task failed");
            Objects.requireNonNull(result, "Documentation task did not set result");
            return result;
        } finally {
            targetClass = null;
        }
    }

    public static DocCommentTree memberDoc(String className, String memberName, JavaFileObject file) {
        try {
            targetClass = className;
            targetMember = memberName;
            var task = task(file, className);
            if (!task.call()) throw new RuntimeException("Documentation task failed");
            Objects.requireNonNull(result, "Documentation task did not set result");
            return result;
        } finally {
            targetClass = null;
            targetMember = null;
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
