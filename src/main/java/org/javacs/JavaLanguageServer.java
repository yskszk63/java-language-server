package org.javacs;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");

    private Path workspaceRoot;
    private CustomLanguageClient client;
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
        var message = new StringJoiner(", ");
        for (var uri : uris) {
            var path = uri.getPath();
            var name = Paths.get(path).getFileName();
            message.add(name.toString());
        }
        LOG.info("Lint " + message);
        publishDiagnostics(uris, compiler.compileBatch(uris).lint());
    }

    private JavaCompilerService createCompiler() {
        Objects.requireNonNull(workspaceRoot, "Can't create compiler because workspaceRoot has not been initialized");

        client.javaStartProgress(new JavaStartProgressParams("Configure javac"));
        client.javaReportProgress(new JavaReportProgressParams("Finding source roots"));

        var sourcePath = InferSourcePath.sourcePath(workspaceRoot); // TODO show each folder as we find it

        // If classpath is specified by the user, don't infer anything
        if (!classPath.isEmpty()) {
            client.javaEndProgress();
            return new JavaCompilerService(sourcePath, classPath, Collections.emptySet());
        }
        // Otherwise, combine inference with user-specified external dependencies
        else {
            var infer = new InferConfig(workspaceRoot, externalDependencies);

            client.javaReportProgress(new JavaReportProgressParams("Inferring class path"));
            var classPath = infer.classPath();

            client.javaReportProgress(new JavaReportProgressParams("Inferring doc path"));
            var docPath = infer.buildDocPath();

            client.javaEndProgress();
            return new JavaCompilerService(sourcePath, classPath, docPath);
        }
    }

    void setExternalDependencies(Set<String> externalDependencies) {
        var changed =
                this.externalDependencies.isEmpty()
                        != externalDependencies.isEmpty(); // TODO shouldn't this be any change?
        this.externalDependencies = externalDependencies;
        if (changed) this.compiler = createCompiler();
    }

    void setClassPath(Set<Path> classPath) {
        var changed = this.classPath.isEmpty() != classPath.isEmpty(); // TODO shouldn't this be any change?
        this.classPath = classPath;
        if (changed) this.compiler = createCompiler();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        this.workspaceRoot = Paths.get(URI.create(params.getRootUri()));

        var result = new InitializeResult();
        var c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptions(true, List.of(".")));
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);
        c.setSignatureHelpProvider(new SignatureHelpOptions(List.of("(", ",")));
        c.setDocumentFormattingProvider(true);
        c.setCodeLensProvider(new CodeLensOptions(true));

        result.setCapabilities(c);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        this.compiler = createCompiler();
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

    void installClient(CustomLanguageClient client) {
        this.client = client;
    }

    CustomLanguageClient client() {
        return this.client;
    }
}
