package org.javacs;

import org.eclipse.lsp4j.InitializeParams;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

public class LanguageServerFixture {

    public static JavaLanguageServer getJavaLanguageServer() {
        Set<Path> classPath = Collections.emptySet();
        Set<Path> sourcePath = Collections.singleton(Paths.get("src/test/resources").toAbsolutePath());
        Path outputDirectory = Paths.get("out").toAbsolutePath();
        JavacHolder javac = new JavacHolder(classPath, sourcePath, outputDirectory, true);
        JavaLanguageServer server = new JavaLanguageServer(javac);

        InitializeParams init = new InitializeParams();
        String workspaceRoot = Paths.get(".").toAbsolutePath().toString();

        init.setRootPath(workspaceRoot);

        server.initialize(init);
        return server;
    }
}
