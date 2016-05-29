package org.javacs;

import java.nio.file.Path;

public class NoJavaConfigException extends RuntimeException {
    public NoJavaConfigException(Path forFile) {
        super("Can't find configuration file for " + forFile);
    }
}