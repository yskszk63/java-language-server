package org.javacs;

import java.time.Instant;
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

/**
 * An implementation of JavaFileManager that removes any .java source files where there is an up-to-date .class file
 */
class IncrementalFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Set<URI> warnedHidden = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

    private boolean hasUpToDateClassFile(String qualifiedName) {
        try {
            JavaFileObject sourceFile = super.getJavaFileForInput(StandardLocation.SOURCE_PATH, qualifiedName, JavaFileObject.Kind.SOURCE),
                outputFile = super.getJavaFileForInput(StandardLocation.CLASS_OUTPUT, qualifiedName, JavaFileObject.Kind.CLASS);
            long sourceModified = sourceFile == null ? 0 : sourceFile.getLastModified(),
                outputModified = outputFile == null ? 0 : outputFile.getLastModified();
            boolean hidden = outputModified >= sourceModified;

            if (hidden && !warnedHidden.contains(sourceFile.toUri())) {
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
