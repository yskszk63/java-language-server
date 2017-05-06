package org.javacs;

import com.google.common.collect.Iterables;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.file.JavacFileManager;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JavaFileManager that searches all .java files in workspace.
 * 
 * Unlike the standard JavaFileManager, WorkspaceFileManager does not assume that a java class com.foo.package.MyClass will be located in a directory com/foo/package,
 * and it will find files in the workspace that are not on the source path.
 * 
 * It *does* assume that it will be located in a file named MyClass.java.
 */
class WorkspaceFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final JavacFileManager delegate;

    /**
     * Root of the workspace that is currently open in VSCode
     */
    private final Path workspaceRoot;

    private final Function<URI, Optional<String>> activeContent;

    WorkspaceFileManager(JavacFileManager delegate, Path workspaceRoot, Function<URI, Optional<String>> activeContent) {
        super(delegate);

        this.delegate = delegate;
        this.workspaceRoot = workspaceRoot;
        this.activeContent = activeContent;
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws IOException {
        Iterable<JavaFileObject> files = super.list(location, packageName, kinds, recurse);

        if (location == StandardLocation.SOURCE_PATH) {
            List<JavaFileObject> workspaceFiles = findPackageClasses(packageName);
            
            files = Iterables.concat(files, workspaceFiles);
        }
        
        return files;
    }

    private List<JavaFileObject> findPackageClasses(String packageName) throws IOException {
        PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        return Files.walk(workspaceRoot)
            .filter(java -> match.matches(java.getFileName()))
            .filter(java -> parsePackage(java).equals(packageName))
            .map(java -> delegate.getRegularFile(java.toFile()))
            .collect(Collectors.toList());
    }

    // TODO cache this based on file modification-time/active-set
    private String parsePackage(Path java) {
        URI uri = java.toUri();
        Optional<String> content = activeContent.apply(uri);
        // TODO recognize a file beginning with 'package ...' as a special case and skip parsing
        CompilationUnitTree tree = InferConfig.parse(uri, content);
        ExpressionTree packageTree = tree.getPackageName();

        if (packageTree == null)
            return "";
        else
            return packageTree.toString();
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        JavaFileObject result = super.getJavaFileForInput(location, className, kind);

        if (result != null)
            return result;

        if (location == StandardLocation.SOURCE_PATH) {
            Optional<JavaFileObject> scan = findClass(className);

            return scan.orElse(null);
        }

        return null;
    }

    private Optional<JavaFileObject> findClass(String className) throws IOException {
        int split = className.lastIndexOf('.');
        String packageName = split == -1 ? "" : className.substring(0, split);
        String simpleClassName = className.substring(split + 1);

        return Files.walk(workspaceRoot)
                .filter(java -> java.getFileName().toString().equals(simpleClassName + ".java"))
                .filter(java -> parsePackage(java).equals(packageName))
                .map(java -> delegate.getRegularFile(java.toFile()))
                .findFirst();
    }

    private static Path tempOutputDirectory(String name) {
        try {
            return Files.createTempDirectory("parser-out");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JavaFileObject getRegularFile(File file) {
        return delegate.getRegularFile(file);
    }
}