package org.javacs;

import io.typefox.lsapi.*;
import io.typefox.lsapi.Diagnostic;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.javacs.Main.JSON;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");
    private Path workspaceRoot;
    private NotificationCallback<PublishDiagnosticsParams> publishDiagnostics = p -> {};
    private NotificationCallback<MessageParams> showMessage = m -> {};
    private Map<Path, String> sourceByPath = new HashMap<>();

    @Override
    public InitializeResult initialize(InitializeParams params) {
        workspaceRoot = Paths.get(params.getRootPath());

        InitializeResultImpl result = new InitializeResultImpl();

        ServerCapabilitiesImpl c = new ServerCapabilitiesImpl();

        // TODO incremental mode
        c.setTextDocumentSync(ServerCapabilities.SYNC_FULL);
        c.setDefinitionProvider(true);

        result.setCapabilities(c);

        return result;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void exit() {

    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new TextDocumentService() {
            @Override
            public List<? extends CompletionItem> completion(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletionItem resolveCompletionItem(CompletionItem unresolved) {
                return null;
            }

            @Override
            public Hover hover(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public SignatureHelp signatureHelp(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public List<? extends Location> definition(TextDocumentPositionParams position) {
                return gotoDefinition(position);
            }

            @Override
            public List<? extends Location> references(ReferenceParams params) {
                return null;
            }

            @Override
            public DocumentHighlight documentHighlight(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public List<? extends SymbolInformation> documentSymbol(DocumentSymbolParams params) {
                return null;
            }

            @Override
            public List<? extends Command> codeAction(CodeActionParams params) {
                return null;
            }

            @Override
            public List<? extends CodeLens> codeLens(CodeLensParams params) {
                return null;
            }

            @Override
            public CodeLens resolveCodeLens(CodeLens unresolved) {
                return null;
            }

            @Override
            public List<? extends TextEdit> formatting(DocumentFormattingParams params) {
                return null;
            }

            @Override
            public List<? extends TextEdit> rangeFormatting(DocumentRangeFormattingParams params) {
                return null;
            }

            @Override
            public List<? extends TextEdit> onTypeFormatting(DocumentOnTypeFormattingParams params) {
                return null;
            }

            @Override
            public WorkspaceEdit rename(RenameParams params) {
                return null;
            }

            @Override
            public void didOpen(DidOpenTextDocumentParams params) {
                TextDocumentItem document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                Path path = getFilePath(uri);
                String text = document.getText();

                sourceByPath.put(path, text);

                doLint(path);
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
                VersionedTextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                Path path = getFilePath(uri);

                for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
                    // TODO incremental updates
                    String text = change.getText();

                    sourceByPath.put(path, text);
                }
            }

            @Override
            public void didClose(DidCloseTextDocumentParams params) {
                // remove from sourceByPath???
            }

            @Override
            public void didSave(DidSaveTextDocumentParams params) {
                TextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                Path path = getFilePath(uri);

                doLint(path);
            }

            @Override
            public void onPublishDiagnostics(NotificationCallback<PublishDiagnosticsParams> callback) {
                publishDiagnostics = callback;
            }
        };
    }

    private Path getFilePath(URI uri) {
        if (!uri.getScheme().equals("file")) {
            MessageParamsImpl message = new MessageParamsImpl();

            message.setMessage(uri + " is not a file");
            message.setType(MessageParams.TYPE_ERROR);

            throw new ShowMessageException(message, null);
        }

        return Paths.get(uri.getPath());
    }

    private void doLint(Path path) {
        List<DiagnosticImpl> errors = lint(path);

        if (!errors.isEmpty()) {
            PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();

            publish.setDiagnostics(errors);
            publish.setUri(path.toFile().toURI().toString());

            publishDiagnostics.call(publish);
        }
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public List<? extends SymbolInformation> symbol(WorkspaceSymbolParams params) {
                return null;
            }

            @Override
            public void didChangeConfiguraton(DidChangeConfigurationParams params) {

            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {

            }
        };
    }

    @Override
    public WindowService getWindowService() {
        return new WindowService() {
            @Override
            public void onShowMessage(NotificationCallback<MessageParams> callback) {
                showMessage = callback;
            }

            @Override
            public void onShowMessageRequest(NotificationCallback<ShowMessageRequestParams> callback) {

            }

            @Override
            public void onLogMessage(NotificationCallback<MessageParams> callback) {

            }
        };
    }

    public List<DiagnosticImpl> lint(Path path) {
        LOG.info("Lint " + path);

        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();

        JavacHolder compiler = findCompiler(path);
        JavaFileObject file = findFile(compiler, path);

        compiler.onError(errors);
        compiler.compile(compiler.parse(file));

        return errors
                .getDiagnostics()
                .stream()
                .filter(e -> e.getStartPosition() != javax.tools.Diagnostic.NOPOS)
                .filter(e -> e.getSource().toUri().getPath().equals(path.toString()))
                .map(error -> {
                    RangeImpl range = position(error);
                    DiagnosticImpl diagnostic = new DiagnosticImpl();

                    diagnostic.setSeverity(Diagnostic.SEVERITY_ERROR);
                    diagnostic.setRange(range);
                    diagnostic.setCode(error.getCode());
                    diagnostic.setMessage(error.getMessage(null));

                    return diagnostic;
                })
                .collect(Collectors.toList());
    }

    private Map<JavacConfig, JavacHolder> compilerCache = new HashMap<>();

    /**
     * Look for a configuration in a parent directory of uri
     */
    private JavacHolder findCompiler(Path path) {
        Path dir = path.getParent();
        Optional<JavacConfig> config = findConfig(dir);
        Optional<JavacHolder> maybeHolder = config.map(c -> compilerCache.computeIfAbsent(c, this::newJavac));

        return maybeHolder.orElseThrow(() -> {
            MessageParamsImpl message = new MessageParamsImpl();

            message.setMessage("Can't find configuration file for " + path);
            message.setType(MessageParams.TYPE_WARNING);

            return new ShowMessageException(message, null);
        });
    }

    private JavacHolder newJavac(JavacConfig c) {
        return new JavacHolder(c.classPath,
                               c.sourcePath,
                               c.outputDirectory);
    }

    // TODO invalidate cache when VSCode notifies us config file has changed
    private Map<Path, Optional<JavacConfig>> configCache = new HashMap<>();

    private Optional<JavacConfig> findConfig(Path dir) {
        return configCache.computeIfAbsent(dir, this::doFindConfig);
    }

    private Optional<JavacConfig> doFindConfig(Path dir) {
        try {
            while (true) {
                Optional<JavacConfig> found = Files.list(dir)
                                                   .flatMap(this::streamIfConfig)
                                                   .sorted((x, y) -> Integer.compare(x.precedence, y.precedence))
                                                   .findFirst();

                if (found.isPresent())
                    return found;
                else if (workspaceRoot.startsWith(dir))
                    return Optional.empty();
                else
                    dir = dir.getParent();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<JavacConfig> streamIfConfig(Path configFile) {
        Optional<JavacConfig> config = readIfConfig(configFile);

        if (config.isPresent())
            return Stream.of(config.get());
        else
            return Stream.empty();
    }

    /**
     * If configFile is a config file, for example javaconfig.json or an eclipse project file, read it.
     */
    private Optional<JavacConfig> readIfConfig(Path configFile) {
        String fileName = configFile.getFileName().toString();

        if (fileName.equals("javaconfig.json")) {
            JavaConfigJson json = readJavaConfigJson(configFile);
            Path dir = configFile.getParent();
            Path classPathFilePath = dir.resolve(json.classPathFile);
            Set<Path> classPath = readClassPathFile(classPathFilePath);
            Set<Path> sourcePath = json.sourcePath.stream().map(dir::resolve).collect(Collectors.toSet());
            Path outputDirectory = dir.resolve(json.outputDirectory);
            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory, 0);

            return Optional.of(config);
        }
        // TODO add more file types
        else {
            return Optional.empty();
        }
    }

    private JavaConfigJson readJavaConfigJson(Path configFile) {
        try {
            return JSON.readValue(configFile.toFile(), JavaConfigJson.class);
        } catch (IOException e) {
            MessageParamsImpl message = new MessageParamsImpl();

            message.setMessage("Error reading " + configFile);
            message.setType(MessageParams.TYPE_ERROR);

            throw new ShowMessageException(message, e);
        }
    }

    private Set<Path> readClassPathFile(Path classPathFilePath) {
        try {
            InputStream in = Files.newInputStream(classPathFilePath);
            String text = new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining());
            Path dir = classPathFilePath.getParent();

            return Arrays.stream(text.split(":"))
                         .map(dir::resolve)
                         .collect(Collectors.toSet());
        } catch (IOException e) {
            MessageParamsImpl message = new MessageParamsImpl();

            message.setMessage("Error reading " + classPathFilePath);
            message.setType(MessageParams.TYPE_ERROR);

            throw new ShowMessageException(message, e);
        }
    }

    private JavaFileObject findFile(JavacHolder compiler, Path path) {
        if (sourceByPath.containsKey(path))
            return new StringFileObject(sourceByPath.get(path), path);
        else
            return compiler.fileManager.getRegularFile(path.toFile());
    }

    private RangeImpl position(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        // Compute start position
        PositionImpl start = new PositionImpl();

        start.setLine((int) (error.getLineNumber() - 1));
        start.setCharacter((int) (error.getColumnNumber() - 1));

        // Compute end position
        PositionImpl end = endPosition(error);

        // Combine into Range
        RangeImpl range = new RangeImpl();

        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    private PositionImpl endPosition(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        try (Reader reader = error.getSource().openReader(true)) {
            long startOffset = error.getStartPosition();
            long endOffset = error.getEndPosition();

            reader.skip(startOffset);

            int line = (int) error.getLineNumber() - 1;
            int column = (int) error.getColumnNumber() - 1;

            for (long i = startOffset; i < endOffset; i++) {
                int next = reader.read();

                if (next == '\n') {
                    line++;
                    column = 0;
                }
                else
                    column++;
            }

            PositionImpl end = new PositionImpl();

            end.setLine(line);
            end.setCharacter(column);

            return end;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<LocationImpl> gotoDefinition(TextDocumentPositionParams position) {
        Path path = getFilePath(URI.create(position.getTextDocument().getUri()));
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        JavacHolder compiler = findCompiler(path);
        JavaFileObject file = findFile(compiler, path);
        long cursor = findOffset(file, position.getPosition().getLine(), position.getPosition().getCharacter());
        GotoDefinitionVisitor visitor = new GotoDefinitionVisitor(file, cursor, compiler.context);

        compiler.afterAnalyze(visitor);
        compiler.onError(errors);
        compiler.compile(compiler.parse(file));

        List<LocationImpl> result = new ArrayList<>();

        for (SymbolLocation locate : visitor.definitions) {
            URI uri = locate.file.toUri();
            Path symbolPath = Paths.get(uri);
            JavaFileObject symbolFile = findFile(compiler, symbolPath);
            RangeImpl range = findPosition(symbolFile, locate.startPosition, locate.endPosition);
            LocationImpl location = new LocationImpl();

            location.setRange(range);
            location.setUri(uri.toString());

            result.add(location);
        }

        return result;
    }

    private static RangeImpl findPosition(JavaFileObject file, long startOffset, long endOffset) {
        try (Reader in = file.openReader(true)) {
            long offset = 0;
            int line = 0;
            int character = 0;

            // Find the start position
            while (offset < startOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            PositionImpl start = createPosition(line, character);

            // Find the end position
            while (offset < endOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            PositionImpl end = createPosition(line, character);

            // Combine into range
            RangeImpl range = new RangeImpl();

            range.setStart(start);
            range.setEnd(end);

            return range;
        } catch (IOException e) {
            throw ShowMessageException.error(e.getMessage(), e);
        }
    }

    private static PositionImpl createPosition(int line, int character) {
        PositionImpl p = new PositionImpl();

        p.setLine(line);
        p.setCharacter(character);

        return p;
    }

    private static long findOffset(JavaFileObject file, int targetLine, int targetCharacter) {
        try (Reader in = file.openReader(true)) {
            long offset = 0;
            int line = 0;
            int character = 0;

            while (line < targetLine) {
                int next = in.read();

                if (next < 0)
                    return offset;
                else {
                    offset++;

                    if (next == '\n')
                        line++;
                }
            }

            while (character < targetCharacter) {
                int next = in.read();

                if (next < 0)
                    return offset;
                else {
                    offset++;
                    character++;
                }
            }

            return offset;
        } catch (IOException e) {
            throw ShowMessageException.error(e.getMessage(), e);
        }
    }
}
