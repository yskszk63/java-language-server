package org.javacs;

import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

class HideModuleInfo implements StandardJavaFileManager {
    private final StandardJavaFileManager delegate;

    HideModuleInfo(StandardJavaFileManager delegate) {
        this.delegate = delegate;
    }

    private boolean notModuleInfo(JavaFileObject file) {
        return file == null || !file.getName().endsWith("module-info.java");
    }

    private boolean notModuleInfo(File file) {
        return file == null || !file.getName().endsWith("module-info.java");
    }

    private boolean notModuleInfo(Path file) {
        return file == null || !file.getFileName().endsWith("module-info.java");
    }

    private JavaFileObject skipModuleInfo(JavaFileObject file) {
        if (file == null) return null;
        if (file.getName().endsWith("module-info.java")) return null;
        else return file;
    }

    private FileObject skipModuleInfo(FileObject file) {
        if (file == null) return null;
        if (file.getName().endsWith("module-info.java")) return null;
        else return file;
    }

    private Iterable<? extends JavaFileObject> removeModuleInfo(Iterable<? extends JavaFileObject> in) {
        return Iterables.filter(in, this::notModuleInfo);
    }

    private Iterable<JavaFileObject> removeModuleInfoInvariant(Iterable<JavaFileObject> in) {
        return Iterables.filter(in, this::notModuleInfo);
    }

    private Iterable<? extends File> removeModuleInfoFile(Iterable<? extends File> in) {
        return Iterables.filter(in, this::notModuleInfo);
    }

    private Iterable<? extends Path> removeModuleInfoPath(Iterable<? extends Path> in) {
        return Iterables.filter(in, this::notModuleInfo);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return delegate.isSameFile(a, b);
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(Iterable<? extends File> files) {
        return removeModuleInfo(delegate.getJavaFileObjectsFromFiles(files));
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromPaths(Iterable<? extends Path> paths) {
        return removeModuleInfo(delegate.getJavaFileObjectsFromPaths(paths));
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
        return removeModuleInfo(delegate.getJavaFileObjects(files));
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjects(Path... paths) {
        return removeModuleInfo(delegate.getJavaFileObjects(paths));
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
        return removeModuleInfo(delegate.getJavaFileObjectsFromStrings(names));
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
        return removeModuleInfo(delegate.getJavaFileObjects(names));
    }

    @Override
    public void setLocation(Location location, Iterable<? extends File> files) throws IOException {
        delegate.setLocation(location, files);
    }

    @Override
    public void setLocationFromPaths(Location location, Collection<? extends Path> paths) throws IOException {
        delegate.setLocationFromPaths(location, paths);
    }

    @Override
    public void setLocationForModule(Location location, String moduleName, Collection<? extends Path> paths)
            throws IOException {
        delegate.setLocationForModule(location, moduleName, paths);
    }

    @Override
    public Iterable<? extends File> getLocation(Location location) {
        return removeModuleInfoFile(delegate.getLocation(location));
    }

    @Override
    public Iterable<? extends Path> getLocationAsPaths(Location location) {
        return removeModuleInfoPath(delegate.getLocationAsPaths(location));
    }

    @Override
    public Path asPath(FileObject file) {
        return delegate.asPath(file);
    }

    @Override
    public void setPathFactory(PathFactory f) {
        delegate.setPathFactory(f);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return delegate.getClassLoader(location);
    }

    @Override
    public Iterable<JavaFileObject> list(
            Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        return removeModuleInfoInvariant(delegate.list(location, packageName, kinds, recurse));
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        return delegate.inferBinaryName(location, file);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return delegate.handleOption(current, remaining);
    }

    @Override
    public boolean hasLocation(Location location) {
        return delegate.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind)
            throws IOException {
        return skipModuleInfo(delegate.getJavaFileForInput(location, className, kind));
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        return skipModuleInfo(delegate.getJavaFileForOutput(location, className, kind, sibling));
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        return skipModuleInfo(delegate.getFileForInput(location, packageName, relativeName));
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling)
            throws IOException {
        return skipModuleInfo(delegate.getFileForOutput(location, packageName, relativeName, sibling));
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public Location getLocationForModule(Location location, String moduleName) throws IOException {
        return delegate.getLocationForModule(location, moduleName);
    }

    @Override
    public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException {
        return delegate.getLocationForModule(location, fo);
    }

    @Override
    public <S> ServiceLoader<S> getServiceLoader(Location location, Class<S> service) throws IOException {
        return delegate.getServiceLoader(location, service);
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
    public boolean contains(Location location, FileObject fo) throws IOException {
        if (fo.getName().endsWith("module-info.java")) return false;

        return delegate.contains(location, fo);
    }

    @Override
    public int isSupportedOption(String option) {
        return delegate.isSupportedOption(option);
    }
}
