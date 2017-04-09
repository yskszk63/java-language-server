package org.javacs;

import com.google.common.collect.Iterables;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Set;

/**
 * An implementation of JavaFileManager that can't see any .java files.
 * We use this when compiling incrementally: the only file we're interested in, we pass in directly as JavaFileObject.
 * Everything else should be retrieved from precompiled .class files.
 */
class ClassOnlyFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    ClassOnlyFileManager(JavaFileManager delegate) {
        super(delegate);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws IOException {
        Iterable<JavaFileObject> files = super.list(location, packageName, kinds, recurse);

        return Iterables.filter(files, this::canSeeFile);
    }

    private boolean canSeeFile(JavaFileObject f) {
        String uriString = f.toUri().toString();

        return uriString.startsWith("jar:") || uriString.endsWith(".class");
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location,
                                              String className,
                                              JavaFileObject.Kind kind) throws IOException {
        return null;
    }

    @Override
    public FileObject getFileForInput(Location location,
                                      String packageName,
                                      String relativeName) throws IOException {
        return null;
    }
}
