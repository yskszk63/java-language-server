package org.javacs;

import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");

    private Path workspaceRoot;
    private LanguageClient client;
    private Set<String> externalDependencies = Set.of();
    private Set<Path> classPath = Set.of();

    JavaCompilerService compiler;
    final JavaTextDocumentService textDocuments = new JavaTextDocumentService(this);
    final JavaWorkspaceService workspace = new JavaWorkspaceService(this);

    private static DiagnosticSeverity severity(Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
            case OTHER:
            default:
                return DiagnosticSeverity.Information;
        }
    }

    private static Position position(String content, long offset) {
        int line = 0, column = 0;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
                column = 0;
            } else column++;
        }
        return new Position(line, column);
    }

    void publishDiagnostics(Collection<URI> files, List<Diagnostic<? extends JavaFileObject>> javaDiagnostics) {
        for (var f : files) {
            List<org.eclipse.lsp4j.Diagnostic> ds = new ArrayList<>();
            for (var j : javaDiagnostics) {
                if (j.getSource() == null) {
                    LOG.warning("No source in warning " + j.getMessage(null));
                    continue;
                }

                var uri = j.getSource().toUri();
                if (uri.equals(f)) {
                    var content = textDocuments.contents(uri).content;
                    var start = position(content, j.getStartPosition());
                    var end = position(content, j.getEndPosition());
                    var sev = severity(j.getKind());
                    var d = new org.eclipse.lsp4j.Diagnostic();
                    d.setSeverity(sev);
                    d.setRange(new Range(start, end));
                    d.setCode(j.getCode());
                    d.setMessage(j.getMessage(null));
                    ds.add(d);
                }
            }
            client.publishDiagnostics(new PublishDiagnosticsParams(f.toString(), ds));
        }
    }

    void lint(Collection<URI> uris) {
        var paths =
                uris.stream()
                        .filter(uri -> uri.getScheme().equals("file"))
                        .map(uri -> Paths.get(uri))
                        .collect(Collectors.toList());
        LOG.info("Lint " + paths.stream().map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));
        publishDiagnostics(uris, compiler.lint(paths));
    }

    private JavaCompilerService createCompiler() {
        Objects.requireNonNull(workspaceRoot, "Can't create compiler because workspaceRoot has not been initialized");

        // If classpath is specified by the user, don't infer anything
        if (!classPath.isEmpty()) {
            return new JavaCompilerService(
                    InferSourcePath.sourcePath(workspaceRoot), classPath, Collections.emptySet());
        }
        // Otherwise, combine inference with user-specified external dependencies
        else {
            var infer = new InferConfig(workspaceRoot, externalDependencies);
            return new JavaCompilerService(
                    InferSourcePath.sourcePath(workspaceRoot), infer.classPath(), infer.buildDocPath());
        }
    }

    void setExternalDependencies(Set<String> externalDependencies) {
        var changed = this.externalDependencies.isEmpty() != externalDependencies.isEmpty();
        this.externalDependencies = externalDependencies;
        if (changed) this.compiler = createCompiler();
    }

    void setClassPath(Set<Path> classPath) {
        var changed = this.classPath.isEmpty() != classPath.isEmpty();
        this.classPath = classPath;
        if (changed) this.compiler = createCompiler();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        this.workspaceRoot = Paths.get(URI.create(params.getRootUri()));
        this.compiler = createCompiler();

        var result = new InitializeResult();
        var c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptions(true, ImmutableList.of(".")));
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);
        c.setSignatureHelpProvider(new SignatureHelpOptions(ImmutableList.of("(", ",")));
        c.setDocumentFormattingProvider(true);
        c.setCodeLensProvider(new CodeLensOptions(false));

        result.setCapabilities(c);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {}

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocuments;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspace;
    }

    void installClient(LanguageClient client) {
        this.client = client;
    }
}
