package org.javacs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileObject;

class JavacParserHolder {
    private final JavacTool parser = JavacTool.create();
    private final JavacFileManager fileManager =
            parser.getStandardFileManager(__ -> {}, null, Charset.defaultCharset());

    CompilationUnitTree parse(URI uri, Optional<String> content) {
        JavaFileObject file =
                content.map(text -> (JavaFileObject) new StringFileObject(text, uri))
                        .orElseGet(() -> fileManager.getRegularFile(new File(uri)));
        List<String> options =
                ImmutableList.of(
                        "-d", tempOutputDirectory("parser-out").toAbsolutePath().toString());
        JavacTask task =
                parser.getTask(null, fileManager, __ -> {}, options, null, ImmutableList.of(file));

        try {
            List<CompilationUnitTree> trees = Lists.newArrayList(task.parse());

            if (trees.isEmpty())
                throw new RuntimeException("Parsing " + file + " produced 0 results");
            else if (trees.size() == 1) return trees.get(0);
            else
                throw new RuntimeException(
                        "Parsing " + file + " produced " + trees.size() + " results");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path tempOutputDirectory(String name) {
        try {
            return Files.createTempDirectory(name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
