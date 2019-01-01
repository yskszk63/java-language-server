package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.*;

public class Docs {

    /** File manager with source-path + platform sources, which we will use to look up individual source files */
    private final StandardJavaFileManager fileManager;

    private static Path srcZip() {
        try {
            var fs = FileSystems.newFileSystem(Lib.SRC_ZIP, Docs.class.getClassLoader());
            return fs.getPath("/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Docs(Set<Path> sourcePath) {
        this.fileManager =
                ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

        // Compute the full source path, including src.zip for platform classes
        var sourcePathFiles = sourcePath.stream().map(Path::toFile).collect(Collectors.toSet());

        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePathFiles);
            fileManager.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, Set.of(srcZip()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<JavaFileObject> find(Ptr ptr) {
        LOG.info(String.format("...looking for file for `%s`...", ptr));

        // Find the file el was declared in
        var className = ptr.qualifiedClassName();
        try {
            var fromSourcePath =
                    fileManager.getJavaFileForInput(
                            StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);
            if (fromSourcePath != null) {
                LOG.info(String.format("...found %s on source path", fromSourcePath.toUri()));
                return Optional.of(fromSourcePath);
            }
            for (var module : Classes.JDK_MODULES) {
                var moduleLocation = fileManager.getLocationForModule(StandardLocation.MODULE_SOURCE_PATH, module);
                if (moduleLocation == null) continue;
                var fromModuleSourcePath =
                        fileManager.getJavaFileForInput(moduleLocation, className, JavaFileObject.Kind.SOURCE);
                if (fromModuleSourcePath != null) {
                    LOG.info(String.format("...found %s in module %s of jdk", fromModuleSourcePath.toUri(), module));
                    return Optional.of(fromModuleSourcePath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOG.info(String.format("...couldn't find file for top-level class `%s`", className));
        return Optional.empty();
    }

    public ParseFile parse(JavaFileObject file) {
        // Parse that file
        var task = Parser.parseTask(file);
        CompilationUnitTree root;
        try {
            root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String contents;
        try {
            contents = file.getCharContent(true).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ParseFile(file.toUri(), contents, task, root);
    }

    private static final Pattern HTML_TAG = Pattern.compile("<(\\w+)>");

    private static boolean isHtml(String text) {
        var tags = HTML_TAG.matcher(text);
        while (tags.find()) {
            var tag = tags.group(1);
            var close = String.format("</%s>", tag);
            var findClose = text.indexOf(close, tags.end());
            if (findClose != -1) return true;
        }
        return false;
    }

    /** If `commentText` looks like HTML, convert it to markdown */
    public static String htmlToMarkdown(String commentText) {
        if (isHtml(commentText)) {
            return TipFormatter.asMarkdown(commentText);
        } else return commentText;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
