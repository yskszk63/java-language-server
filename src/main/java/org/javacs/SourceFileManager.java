package org.javacs;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.tools.*;

class SourceFileManager implements StandardJavaFileManager {
    private final StandardJavaFileManager delegate = createDelegateFileManager();

    private final Set<Path> sourcePath, classPath;

    SourceFileManager(Set<Path> sourcePath, Set<Path> classPath) {
        this.sourcePath = sourcePath;
        this.classPath = classPath;
    }

    private static StandardJavaFileManager createDelegateFileManager() {
        var compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
        return compiler.getStandardFileManager(__ -> {}, null, Charset.defaultCharset());
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        var thisClassLoader = getClass().getClassLoader();
        if (location == StandardLocation.CLASS_PATH) {
            return new URLClassLoader(urls(classPath), thisClassLoader);
        } else if (location == StandardLocation.SOURCE_PATH) {
            return new URLClassLoader(urls(sourcePath), thisClassLoader);
        } else {
            return thisClassLoader;
        }
    }

    private URL[] urls(Set<Path> paths) {
        var urls = new URL[paths.size()];
        var i = 0;
        for (var p : paths) {
            try {
                urls[i++] = p.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return urls;
    }

    @Override
    public Iterable<JavaFileObject> list(
            Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        if (location == StandardLocation.CLASS_PATH) {
            return list(classPath, this::isClassPathFile);
        } else if (location == StandardLocation.SOURCE_PATH) {
            return list(sourcePath, this::isJavaSource);
        } else {
            return delegate.list(location, packageName, kinds, recurse);
        }
    }

    private boolean isJavaSource(JavaFileObject file) {
        return file.getName().endsWith(".java");
    }

    private boolean isClassPathFile(JavaFileObject file) {
        var name = file.getName();
        return name.endsWith(".class") || name.endsWith(".jar");
    }

    private Iterable<JavaFileObject> list(Set<Path> dirs, Predicate<JavaFileObject> filter) {
        var stream = dirs.stream().flatMap(this::list).flatMap(this::asJavaFileObjects).filter(filter);
        return stream::iterator;
    }

    private Stream<Path> list(Path dir) {
        try {
            if (!Files.exists(dir)) return Stream.of();
            return Files.walk(dir).filter(Files::isRegularFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<JavaFileObject> asJavaFileObjects(Path file) {
        var fileName = file.getFileName().toString();
        if (fileName.endsWith(".jar")) {
            JarFile jar;
            try {
                jar = new JarFile(file.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return jar.stream().map(entry -> new JarFileObject(file, entry));
        } else {
            return Stream.of(new SourceFileObject(file));
        }
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (location == StandardLocation.SOURCE_PATH) {
            var source = (SourceFileObject) file;
            return sourceFileBinaryName(sourcePath, source);
        } else if (location == StandardLocation.CLASS_PATH && file instanceof SourceFileObject) {
            var source = (SourceFileObject) file;
            return sourceFileBinaryName(classPath, source);
        } else if (location == StandardLocation.CLASS_PATH && file instanceof JarFileObject) {
            var jarFile = (JarFileObject) file;
            var relativePath = jarFile.entry.getName();
            return binaryName(relativePath);
        } else {
            return delegate.inferBinaryName(location, file);
        }
    }

    private String sourceFileBinaryName(Set<Path> path, SourceFileObject source) {
        for (var root : path) {
            if (source.path.startsWith(root)) {
                var relativePath = root.relativize(source.path).toString();
                return binaryName(relativePath);
            }
        }
        return null;
    }

    private String binaryName(String relativePath) {
        var slash = removeExtension(relativePath);
        return slash.replace('/', '.');
    }

    private String removeExtension(String fileName) {
        var lastDot = fileName.lastIndexOf(".");
        return (lastDot == -1 ? fileName : fileName.substring(0, lastDot));
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return a.equals(b);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return false;
    }

    @Override
    public boolean hasLocation(Location location) {
        return location == StandardLocation.CLASS_PATH
                || location == StandardLocation.SOURCE_PATH
                || delegate.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
            throws IOException {
        if (location == StandardLocation.SOURCE_PATH || location == StandardLocation.CLASS_PATH) {
            var relative = className.replace('.', '/') + kind.extension;
            return findFileForInput(location, relative);
        }
        return delegate.getJavaFileForInput(location, className, kind);
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        if (location == StandardLocation.SOURCE_PATH || location == StandardLocation.CLASS_PATH) {
            var relative = relativeName;
            if (!packageName.isEmpty()) {
                relative = packageName.replace('.', '/') + '/' + relative;
            }
            return findFileForInput(location, relative);
        }
        return delegate.getFileForInput(location, packageName, relativeName);
    }

    private JavaFileObject findFileForInput(Location location, String relative) {
        if (location == StandardLocation.CLASS_PATH) {
            for (var root : classPath) {
                if (Files.isDirectory(root)) {
                    var absolute = root.resolve(relative);
                    if (Files.exists(absolute)) {
                        return new SourceFileObject(root);
                    }
                } else if (root.getFileName().toString().endsWith(".jar")) {
                    JarFile jar;
                    try {
                        jar = new JarFile(root.toFile());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    var entry = jar.getJarEntry(relative);
                    if (entry != null) {
                        return new JarFileObject(root, entry);
                    }
                }
            }
        } else if (location == StandardLocation.SOURCE_PATH) {
            for (var root : sourcePath) {
                var absolute = root.resolve(relative);
                if (Files.exists(absolute)) {
                    return new SourceFileObject(root);
                }
            }
        }
        return null;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flush() throws IOException {}

    @Override
    public void close() throws IOException {}

    @Override
    public int isSupportedOption(String option) {
        return -1;
    }

    @Override
    public Location getLocationForModule(Location location, String moduleName) throws IOException {
        return delegate.getLocationForModule(location, moduleName);
    }

    @Override
    public Location getLocationForModule(Location location, JavaFileObject file) throws IOException {
        return delegate.getLocationForModule(location, file);
    }

    @Override
    public <S> ServiceLoader<S> getServiceLoader(Location location, Class<S> service) throws IOException {
        getClass().getModule().addUses(service);
        return ServiceLoader.load(service, getClassLoader(location));
    }

    @Override
    public String inferModuleName(Location location) throws IOException {
        return delegate.inferModuleName(location);
    }

    @Override
    public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
        return delegate.listLocationsForModules(location);
    }

    @Override
    public boolean contains(Location location, FileObject file) throws IOException {
        if (file instanceof SourceFileObject && location == StandardLocation.SOURCE_PATH) {
            var source = (SourceFileObject) file;
            return contains(sourcePath, source);
        } else if (file instanceof JarFileObject) {
            var jarFile = (JarFileObject) file;
            for (var jar : classPath) {
                if (jarFile.jar.equals(jar)) {
                    return true;
                }
            }
            return false;
        } else {
            return delegate.contains(location, file);
        }
    }

    private boolean contains(Set<Path> location, SourceFileObject source) {
        for (var root : location) {
            if (source.path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
        var result = new ArrayList<JavaFileObject>();
        for (var f : files) {
            result.add(new SourceFileObject(f.toPath()));
        }
        return result;
    }

    // Just for compatibility with StandardJavaFileManager
    // TODO delete this once we no longer need the useSourceFileManager flag

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLocation(Location location, Iterable<? extends File> files) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends File> getLocation(Location location) {
        throw new UnsupportedOperationException();
    }
}
