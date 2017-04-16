package org.javacs;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class LanguageServerFixture {

    static {
        Main.setRootFormat();
    }

    static JavaLanguageServer getJavaLanguageServer() {
        return getJavaLanguageServer(new LanguageClient() {
            @Override
            public void telemetryEvent(Object o) {

            }

            @Override
            public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {

            }

            @Override
            public void showMessage(MessageParams messageParams) {

            }

            @Override
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams) {
                return null;
            }

            @Override
            public void logMessage(MessageParams messageParams) {

            }
        });
    }

    static JavaLanguageServer getJavaLanguageServer(LanguageClient client) {
        Set<Path> classPath = Collections.emptySet();
        Set<Path> sourcePath = Collections.singleton(Paths.get("src/test/resources").toAbsolutePath());
        Path outputDirectory = Paths.get("target/test-output").toAbsolutePath();
        JavacHolder javac = JavacHolder.create(classPath, sourcePath, outputDirectory);
        JavaLanguageServer server = new JavaLanguageServer(javac);

        InitializeParams init = new InitializeParams();
        String workspaceRoot = Paths.get(".").toAbsolutePath().toString();

        init.setRootPath(workspaceRoot);

        server.initialize(init);
        server.installClient(client);

        server.maxItems = 100;

        return server;
    }
}
