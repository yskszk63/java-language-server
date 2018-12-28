package org.javacs;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.javacs.lsp.*;

class JavaWorkspaceService implements WorkspaceService {
    private static final Logger LOG = Logger.getLogger("main");

    private final JavaLanguageServer server;

    JavaWorkspaceService(JavaLanguageServer server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        List<SymbolInformation> list =
                server.compiler
                        .findSymbols(params.getQuery())
                        .map(Parser::asSymbolInformation)
                        .limit(50)
                        .collect(Collectors.toList());
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {
        var settings = (JsonObject) change.getSettings();
        var java = settings.getAsJsonObject("java");

        var externalDependencies = java.getAsJsonArray("externalDependencies");
        var strings = new HashSet<String>();
        for (var each : externalDependencies) strings.add(each.getAsString());
        server.setExternalDependencies(strings);

        var classPath = java.getAsJsonArray("classPath");
        var paths = new HashSet<Path>();
        for (var each : classPath) paths.add(Paths.get(each.getAsString()).toAbsolutePath());
        server.setClassPath(paths);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
