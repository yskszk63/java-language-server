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

        var docs = env.getDocTrees();
        var elements = env.getElementUtils();
        var target = elements.getTypeElement(targetClass);
        if (targetMember == null) {
            result = docs.getDocCommentTree(target);
        } else {
            for (var member : elements.getAllMembers(target)) {
                if (member.getSimpleName().contentEquals(targetMember)) {
                    result = docs.getDocCommentTree(member);
                }
            }
        }
        return true;
    }

    /** Empty file manager we pass to javadoc to prevent it from roaming all over the place */
    private static final StandardJavaFileManager emptyFileManager =
            ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

    public static DocCommentTree classDoc(String className, JavaFileObject file) {
        try {
            targetClass = className;
            var tool = ToolProvider.getSystemDocumentationTool();
            var task = tool.getTask(null, emptyFileManager, __ -> {}, DocsIndexer.class, List.of(), List.of(file));
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
            var tool = ToolProvider.getSystemDocumentationTool();
            var task = tool.getTask(null, emptyFileManager, __ -> {}, DocsIndexer.class, List.of(), List.of(file));
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
