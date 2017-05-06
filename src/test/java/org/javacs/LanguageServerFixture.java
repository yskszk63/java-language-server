package org.javacs;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.nio.file.Path;
import java.nio.file.Paths;
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
        Path workspaceRoot = Paths.get("src/test/test-project/workspace").toAbsolutePath();
        JavaLanguageServer server = new JavaLanguageServer();

        InitializeParams init = new InitializeParams();

        init.setRootPath(workspaceRoot.toString());

        server.initialize(init);
        server.installClient(client);

        server.maxItems = 100;

        return server;
    }
}
