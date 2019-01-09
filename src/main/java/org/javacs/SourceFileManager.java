package org.javacs;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import javax.tools.*;

class SourceFileManager implements StandardJavaFileManager {
    private final StandardJavaFileManager delegate = createDelegateFileManager();

    private static StandardJavaFileManager createDelegateFileManager() {
        var compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
        return compiler.getStandardFileManager(SourceFileManager::logError, null, Charset.defaultCharset());
    }

    private static void logError(Diagnostic<?> error) {
        LOG.warning(error.getMessage(null));
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return delegate.getClassLoader(location);
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
        if (location == StandardLocation.SOURCE_PATH) {
            var stream = FileStore.list(packageName).stream().map(this::asJavaFileObject).filter(this::isJavaSource);
            return stream::iterator;
        } else {
            return delegate.list(location, packageName, kinds, recurse);
        }
    }

    private boolean isJavaSource(JavaFileObject file) {
        return FileStore.isJavaFile(file.toUri());
    }

    private JavaFileObject asJavaFileObject(Path file) {
        return new SourceFileObject(file);
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (location == StandardLocation.SOURCE_PATH) {
            var source = (SourceFileObject) file;
            var packageName = FileStore.packageName(source.path);
            var className = removeExtension(source.path.getFileName().toString());
            if (!packageName.isEmpty()) className = packageName + "." + className;
            return className;
        } else {
            return delegate.inferBinaryName(location, file);
        }
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
        return delegate.handleOption(current, remaining);
    }

    @Override
    public boolean hasLocation(Location location) {
        return location == StandardLocation.SOURCE_PATH || delegate.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
            throws IOException {
        if (location == StandardLocation.SOURCE_PATH) {
            var packageName = Parser.mostName(className);
            var simpleClassName = Parser.lastName(className);
            for (var f : FileStore.list(packageName)) {
                if (f.getFileName().toString().equals(simpleClassName + kind.extension)) return new SourceFileObject(f);
            }
            return null;
        }
        return delegate.getJavaFileForInput(location, className, kind);
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        if (location == StandardLocation.SOURCE_PATH) {
            return null;
        }
        return delegate.getFileForInput(location, packageName, relativeName);
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
        return delegate.isSupportedOption(option);
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
        if (location == StandardLocation.SOURCE_PATH) {
            var source = (SourceFileObject) file;
            return FileStore.contains(source.path);
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
        delegate.setLocation(location, files);
    }

    @Override
    public Iterable<? extends File> getLocation(Location location) {
        return delegate.getLocation(location);
    }

    private static final Logger LOG = Logger.getLogger("main");
}
