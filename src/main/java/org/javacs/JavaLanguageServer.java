package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");
    int maxItems = 50;
    private Map<URI, VersionedContent> activeDocuments = new HashMap<>();
    private CompletableFuture<LanguageClient> client = new CompletableFuture<>();
    private Path workspaceRoot = Paths.get(".");
    private JavaSettings settings = new JavaSettings();
    private SymbolIndex index;

    JavaLanguageServer() {
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceRoot = Paths.get(params.getRootPath()).toAbsolutePath().normalize();

        createCompiler();

        InitializeResult result = new InitializeResult();

        ServerCapabilities c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptions(true, ImmutableList.of(".")));
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);
        c.setCodeActionProvider(true);
        c.setExecuteCommandProvider(new ExecuteCommandOptions(ImmutableList.of("Java.importClass")));
        c.setSignatureHelpProvider(new SignatureHelpOptions(ImmutableList.of("(", ",")));

        result.setCapabilities(c);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new TextDocumentService() {
            @Override
            public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position) {
                Instant started = Instant.now();
                URI uri = URI.create(position.getTextDocument().getUri());
                Optional<String> content = activeContent(uri);
                int line = position.getPosition().getLine() + 1;
                int character = position.getPosition().getCharacter() + 1;

                LOG.info(String.format("completion at %s %d:%d", uri, line, character));

                JavacHolder compiler = compiler();
                FocusedResult result = compiler.compileFocused(uri, content, line, character, true);
                List<CompletionItem> items = Completions.at(result, index, docs())
                        .limit(maxItems)
                        .collect(Collectors.toList());
                CompletionList list = new CompletionList(items.size() == maxItems, items);
                Duration elapsed = Duration.between(started, Instant.now());
                
                if (list.isIncomplete())
                    LOG.info(String.format("Found %d items (incomplete) in %d ms", items.size(), elapsed.toMillis()));
                else
                    LOG.info(String.format("Found %d items in %d ms", items.size(), elapsed.toMillis()));

                return CompletableFuture.completedFuture(Either.forRight(list));
            }

            @Override
            public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
                return CompletableFutures.computeAsync(cancel -> {
                    docs().resolveCompletionItem(unresolved);

                    return unresolved;
                });
            }

            @Override
            public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
                URI uri = URI.create(position.getTextDocument().getUri());
                Optional<String> content = activeContent(uri);
                int line = position.getPosition().getLine() + 1;
                int character = position.getPosition().getCharacter() + 1;

                LOG.info(String.format("hover at %s %d:%d", uri, line, character));

                JavacHolder compiler = compiler();
                FocusedResult result = compiler.compileFocused(uri, content, line, character, false);
                Hover hover = elementAtCursor(result)
                        .map(this::hoverText)
                        .orElseGet(this::emptyHover);

                return CompletableFuture.completedFuture(hover);
            }

            private Optional<Element> elementAtCursor(FocusedResult compiled) {
                return compiled.cursor.flatMap(cursor -> {
                    Element el = Trees.instance(compiled.task).getElement(cursor);

                    return Optional.ofNullable(el);
                });
            }

            private Hover hoverText(Element el) {
                return Hovers.hoverText(el, docs());
            }

            private Hover emptyHover() {
                return new Hover(Collections.emptyList(), null);
            }

            @Override
            public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
                URI uri = URI.create(position.getTextDocument().getUri());
                Optional<String> content = activeContent(uri);
                int line = position.getPosition().getLine() + 1;
                int character = position.getPosition().getCharacter() + 1;

                LOG.info(String.format("signatureHelp at %s %d:%d", uri, line, character));

                JavacHolder compiler = compiler();
                FocusedResult result = compiler.compileFocused(uri, content, line, character, true);
                SignatureHelp help = Signatures.help(result, line, character, docs()).orElseGet(SignatureHelp::new);

                return CompletableFuture.completedFuture(help);
            }

            @Override
            public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
                URI uri = URI.create(position.getTextDocument().getUri());
                Optional<String> content = activeContent(uri);
                int line = position.getPosition().getLine() + 1;
                int character = position.getPosition().getCharacter() + 1;

                LOG.info(String.format("definition at %s %d:%d", uri, line, character));

                JavacHolder compiler = compiler();
                FocusedResult result = compiler.compileFocused(uri, content, line, character, false);
                List<Location> locations = References.gotoDefinition(result, index)
                        .map(Collections::singletonList)
                        .orElseGet(Collections::emptyList);
                return CompletableFuture.completedFuture(locations);
            }

            @Override
            public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
                URI uri = URI.create(params.getTextDocument().getUri());
                Optional<String> content = activeContent(uri);
                int line = params.getPosition().getLine() + 1;
                int character = params.getPosition().getCharacter() + 1;

                LOG.info(String.format("references at %s %d:%d", uri, line, character));

                JavacHolder compiler = compiler();
                FocusedResult result = compiler.compileFocused(uri, content, line, character, false);
                List<Location> locations = References.findReferences(result, index)
                        .collect(Collectors.toList());

                return CompletableFuture.completedFuture(locations);
            }

            @Override
            public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
                URI uri = URI.create(params.getTextDocument().getUri());
                List<SymbolInformation> symbols = index.allInFile(uri).collect(Collectors.toList());

                return CompletableFuture.completedFuture(symbols);
            }

            @Override
            public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
                // Compilation is expensive
                // Don't do it unless a codeAction is actually possible
                // At the moment we only generate code actions in response to diagnostics
                if (params.getContext().getDiagnostics().isEmpty())
                    return CompletableFuture.completedFuture(Collections.emptyList());

                URI uri = URI.create(params.getTextDocument().getUri());
                int line = params.getRange().getStart().getLine() + 1;
                int character = params.getRange().getStart().getCharacter() + 1;

                LOG.info(String.format("codeAction at %s %d:%d", uri, line, character));

                JavacHolder compiler = compiler();
                List<Command> commands = new CodeActions(compiler, uri, activeContent(uri), line, character, index).find(params);

                return CompletableFuture.completedFuture(commands);
            }

            @Override
            public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
                return null;
            }

            @Override
            public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
                return null;
            }

            @Override
            public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
                return null;
            }

            @Override
            public void didOpen(DidOpenTextDocumentParams params) {
                try {
                    TextDocumentItem document = params.getTextDocument();
                    URI uri = URI.create(document.getUri());

                    activeDocuments.put(uri, new VersionedContent(document.getText(), document.getVersion()));

                    doLint(Collections.singleton(uri));
                } catch (NoJavaConfigException e) {
                    throw ShowMessageException.warning(e.getMessage(), e);
                }
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
                VersionedTextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                VersionedContent existing = activeDocuments.get(uri);
                String newText = existing.content;

                if (document.getVersion() > existing.version) {
                    for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
                        if (change.getRange() == null)
                            activeDocuments.put(uri, new VersionedContent(change.getText(), document.getVersion()));
                        else
                            newText = patch(newText, change);
                    }

                    activeDocuments.put(uri, new VersionedContent(newText, document.getVersion()));
                }
                else LOG.warning("Ignored change with version " + document.getVersion() + " <= " + existing.version);
            }

            @Override
            public void didClose(DidCloseTextDocumentParams params) {
                TextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());

                // Remove from source cache
                activeDocuments.remove(uri);
            }

            @Override
            public void didSave(DidSaveTextDocumentParams params) {
                // Re-lint all active documents
                doLint(activeDocuments.keySet());

                // Re-index javadocs of saved document
                URI uri = URI.create(params.getTextDocument().getUri());
                
                activeContent(uri).ifPresent(content -> doJavadoc(new StringFileObject(content, uri)));
            }
        };
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            Range range = change.getRange();
            BufferedReader reader = new BufferedReader(new StringReader(sourceText));
            StringWriter writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.getStart().getLine()) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.getStart().getCharacter(); character++)
                writer.write(reader.read());

            // Write replacement text
            writer.write(change.getText());

            // Skip replaced text
            reader.skip(change.getRangeLength());

            // Write remaining text
            while (true) {
                int next = reader.read();

                if (next == -1)
                    return writer.toString();
                else
                    writer.write(next);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void doLint(Collection<URI> paths) {
        LOG.info("Lint " + Joiner.on(", ").join(paths));

        JavacHolder compiler = compiler();
        List<javax.tools.Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
        Map<URI, Optional<String>> content = paths.stream()
            .collect(Collectors.toMap(f -> f, this::activeContent));
        BatchResult compile = compiler.compileBatch(content);

        errors.addAll(compile.errors.getDiagnostics());

        publishDiagnostics(paths, errors);
    }

    private void doJavadoc(JavaFileObject source) {
        docs().update(source);
    }

    /**
     * Text of file, if it is in the active set
     */
    Optional<String> activeContent(URI file) {
        return Optional.ofNullable(activeDocuments.get(file))
                .map(doc -> doc.content);
    }

    Map<URI, String> activeDocuments() {
        Map<URI, String> view = Maps.transformValues(activeDocuments, versioned -> versioned.content);

        return Collections.unmodifiableMap(view);
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
                LOG.info(params.toString());

                switch (params.getCommand()) {
                    case "Java.importClass":
                        String fileString = (String) params.getArguments().get(0);
                        URI fileUri = URI.create(fileString);
                        String packageName = (String) params.getArguments().get(1);
                        String className = (String) params.getArguments().get(2);
                        JavacHolder compiler = compiler();
                        FocusedResult compiled = compiler.compileFocused(fileUri, activeContent(fileUri), 1, 1, false);

                        if (compiled.compilationUnit.getSourceFile().toUri().equals(fileUri)) {
                            List<TextEdit> edits = new RefactorFile(compiled.task, compiled.compilationUnit)
                                    .addImport(packageName, className);

                            client.join().applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(
                                    Collections.singletonMap(fileString, edits),
                                    null
                            )));
                        }

                        break;
                    default:
                        LOG.warning("Don't know what to do with " + params.getCommand());
                }

                return CompletableFuture.completedFuture("Done");
            }

            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
                List<SymbolInformation> infos = index.search(params.getQuery()).collect(Collectors.toList());

                return CompletableFuture.completedFuture(infos);
            }

            @Override
            public void didChangeConfiguration(DidChangeConfigurationParams change) {
                settings = Main.JSON.convertValue(change.getSettings(), JavaSettings.class);

                createCompiler();
            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
                for (FileEvent event : params.getChanges()) {
                    if (event.getUri().endsWith(".java")) {
                        if (event.getType() == FileChangeType.Deleted) {
                            URI uri = URI.create(event.getUri());

                            activeDocuments.remove(uri);

                            JavacHolder compiler = compiler();
                            BatchResult result = compiler.delete(uri);

                            publishDiagnostics(Collections.singleton(uri), result.errors.getDiagnostics());
                        }
                    }
                    else if (event.getUri().endsWith("javaconfig.json")) {
                        // TODO invalidate caches when javaconfig.json changes
                    }
                }
            }
        };
    }
    
    void publishDiagnostics(Collection<URI> touched, List<javax.tools.Diagnostic<? extends JavaFileObject>> diagnostics) {
        Map<URI, PublishDiagnosticsParams> files = new HashMap<>();

        touched.forEach(p -> files.put(p, newPublishDiagnostics(p)));
        
        for (javax.tools.Diagnostic<? extends JavaFileObject> error : diagnostics) {
            URI uri = error.getSource().toUri();
            PublishDiagnosticsParams publish = files.computeIfAbsent(uri, this::newPublishDiagnostics);

            Lints.convert(error).ifPresent(publish.getDiagnostics()::add);
        }

        client.thenAccept(resolved -> files.values().forEach(resolved::publishDiagnostics));
    }

    private PublishDiagnosticsParams newPublishDiagnostics(URI newUri) {
        PublishDiagnosticsParams p = new PublishDiagnosticsParams();

        p.setDiagnostics(new ArrayList<>());
        p.setUri(newUri.toString());

        return p;
    }

    private JavacHolder compiler = null;
    private Javadocs docs = null;

    /**
     * Default compiler generated using InferConfig
     */
    JavacHolder compiler() {
        return compiler;
    }

    /**
     * Default javadocs generated using InferConfig
     */
    Javadocs docs() {
        return docs;
    }

    // TODO this function needs to be invoked whenever the user creates a new .java file outside the existing source root
    private void createCompiler() {
        Set<Path> sourcePath = InferConfig.workspaceSourcePath(workspaceRoot);
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path mavenHome = userHome.resolve(".m2");
        Path gradleHome = userHome.resolve(".gradle");
        Path outputDirectory = defaultOutputDirectory();
        List<Artifact> externalDependencies = Lists.transform(settings.java.externalDependencies, Artifact::parse);
        InferConfig infer = new InferConfig(workspaceRoot, externalDependencies, mavenHome, gradleHome, outputDirectory);
        Set<Path> classPath = infer.buildClassPath(),
                docPath = infer.buildDocPath();

        LOG.info("Inferred configuration: ");
        LOG.info("\tsourcePath:" + Joiner.on(' ').join(sourcePath));
        LOG.info("\tclassPath:" + Joiner.on(' ').join(classPath));
        LOG.info("\tdocPath:" + Joiner.on(' ').join(docPath));
        LOG.info("\toutputDirectory:" + outputDirectory);

        this.docs = new Javadocs(sourcePath, docPath, this::activeContent);
        this.compiler = JavacHolder.create(sourcePath, classPath, outputDirectory);
        this.index = new SymbolIndex(this);
    }

    private Path defaultOutputDirectory() {
        try {
            return Files.createTempDirectory("vscode-javac-output"); // TODO this should be consistent within a project
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Element> findSymbol(URI file, int line, int character) {
        Optional<String> content = activeContent(file);
        JavacHolder compiler = compiler();
        FocusedResult result = compiler.compileFocused(file, content, line, character, false);
        Trees trees = Trees.instance(result.task);
        Function<TreePath, Optional<Element>> findSymbol = cursor -> Optional.ofNullable(trees.getElement(cursor));

        return result.cursor.flatMap(findSymbol);
    }

    void installClient(LanguageClient client) {
        this.client.complete(client);

        Logger.getLogger("").addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();

                if (record.getThrown() != null) {
                    StringWriter trace = new StringWriter();

                    record.getThrown().printStackTrace(new PrintWriter(trace));
                    message += "\n" + trace;
                }

                client.logMessage(new MessageParams(
                        messageType(record.getLevel().intValue()),
                        message
                ));
            }

            private MessageType messageType(int level) {
                if (level >= Level.SEVERE.intValue())
                    return MessageType.Error;
                else if (level >= Level.WARNING.intValue())
                    return MessageType.Warning;
                else if (level >= Level.INFO.intValue())
                    return MessageType.Info;
                else
                    return MessageType.Log;
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {

            }
        });
    }

    Path workspaceRoot() {
        Objects.requireNonNull(workspaceRoot, "Language server has not been initialized");

        return workspaceRoot;
    }
}
