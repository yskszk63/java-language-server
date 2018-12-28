package org.javacs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.javacs.lsp.*;

class LanguageServerFixture {

    public static Path DEFAULT_WORKSPACE_ROOT = Paths.get("src/test/test-project/workspace").toAbsolutePath();

    static {
        Main.setRootFormat();
    }

    static JavaLanguageServer getJavaLanguageServer() {
        return getJavaLanguageServer(DEFAULT_WORKSPACE_ROOT, diagnostic -> LOG.info(diagnostic.getMessage()));
    }

    static JavaLanguageServer getJavaLanguageServer(Path workspaceRoot, Consumer<Diagnostic> onError) {
        return getJavaLanguageServer(
                workspaceRoot,
                new CustomLanguageClient() {
                    @Override
                    public void telemetryEvent(Object o) {}

                    @Override
                    public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {
                        publishDiagnosticsParams.getDiagnostics().forEach(onError);
                    }

                    @Override
                    public void showMessage(MessageParams messageParams) {}

                    @Override
                    public CompletableFuture<MessageActionItem> showMessageRequest(
                            ShowMessageRequestParams showMessageRequestParams) {
                        return null;
                    }

                    @Override
                    public void logMessage(MessageParams messageParams) {}

                    @Override
                    public void javaStartProgress(JavaStartProgressParams params) {}

                    @Override
                    public void javaReportProgress(JavaReportProgressParams params) {}

                    @Override
                    public void javaEndProgress() {}
                });
    }

    private static JavaLanguageServer getJavaLanguageServer(Path workspaceRoot, CustomLanguageClient client) {
        var server = new JavaLanguageServer();
        var init = new InitializeParams();

        init.setRootUri(workspaceRoot.toUri().toString());

        server.installClient(client);
        server.initialize(init);
        server.initialized(null);

        return server;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
