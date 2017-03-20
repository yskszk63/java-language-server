package org.javacs;

import com.sun.tools.javac.code.Symbol;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.tools.JavaFileObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.javacs.Main.JSON;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");
    private Path workspaceRoot;
    private Map<URI, String> activeDocuments = new HashMap<>();
    private LanguageClient client;

    public JavaLanguageServer() {
        this.testJavac = Optional.empty();
    }

    public JavaLanguageServer(JavacHolder testJavac) {
        this.testJavac = Optional.of(testJavac);
    }

    public void onError(String message, Throwable error) {
        if (error instanceof ShowMessageException)
            client.showMessage(((ShowMessageException) error).message);
        else if (error instanceof NoJavaConfigException) {
            // Swallow error
            // If you want to show a message for no-java-config, 
            // you have to specifically catch the error lower down and re-throw it
            LOG.warning(error.getMessage());
        }
        else {
            LOG.log(Level.SEVERE, message, error);
            
            MessageParams m = new MessageParams();

            m.setMessage(message);
            m.setType(MessageType.Error);

            client.showMessage(m);
        }
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceRoot = Paths.get(params.getRootPath()).toAbsolutePath().normalize();

        InitializeResult result = new InitializeResult();

        ServerCapabilities c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptions());
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);

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
            public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
                return CompletableFuture.completedFuture(autocomplete(position));
            }

            @Override
            public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
                return null;
            }

            @Override
            public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
                return CompletableFuture.completedFuture(doHover(position));
            }

            @Override
            public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
                return CompletableFuture.completedFuture(gotoDefinition(position));
            }

            @Override
            public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
                return CompletableFuture.completedFuture(findReferences(params));
            }

            @Override
            public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
                return CompletableFuture.completedFuture(findDocumentSymbols(params));
            }

            @Override
            public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
                return null;
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
                    String text = document.getText();

                    activeDocuments.put(uri, text);

                    doLint(Collections.singleton(uri));
                } catch (NoJavaConfigException e) {
                    throw ShowMessageException.warning(e.getMessage(), e);
                }
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
                VersionedTextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());

                for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
                    if (change.getRange() == null)
                        activeDocuments.put(uri, change.getText());
                    else {
                        String existingText = activeDocuments.get(uri);
                        String newText = patch(existingText, change);

                        activeDocuments.put(uri, newText);
                    }
                }
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
                TextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                // TODO can we just re-line uri?

                // Re-lint all active documents
                // 
                // We would prefer to just re-lint the documents that the user can see
                // But there is no didSwitchTo(document) event, so we have no way of knowing when the user switches between tabs
                // Therefore, we just re-lint all open editors
                doLint(activeDocuments.keySet());
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
        LOG.info("Lint " + paths);

        Map<JavacConfig, Map<URI, Optional<String>>> files = new HashMap<>();

        for (URI each : paths) {
            dir(each).flatMap(this::findConfig).ifPresent(config -> {
                files.computeIfAbsent(config, newConfig -> new HashMap<>()).put(each, activeContent(each));
            });
        }

        files.forEach((config, configFiles) -> {
            CompilationResult compile = findCompilerForConfig(config).compile(configFiles);

            publishDiagnostics(compile);
        });
    }

    /**
     * Text of file, if it is in the active set
     */
    private Optional<String> activeContent(URI file) {
        return Optional.ofNullable(activeDocuments.get(file));
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
                List<SymbolInformation> infos = compilerCache
                        .values()
                        .stream()
                        .flatMap(compiler -> compiler.index.search(params.getQuery()))
                        .limit(100)
                        .collect(Collectors.toList());

                return CompletableFuture.completedFuture(infos);
            }

            @Override
            public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {

            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
                for (FileEvent event : params.getChanges()) {
                    if (event.getUri().endsWith(".java")) {
                        if (event.getType() == FileChangeType.Deleted) {
                            URI uri = URI.create(event.getUri());

                            activeDocuments.remove(uri);

                            findCompiler(uri).ifPresent(compiler -> {
                                CompilationResult result = compiler.delete(uri);

                                publishDiagnostics(result);
                            });
                        }
                    }
                    else if (event.getUri().endsWith("javaconfig.json")) {
                        // TODO invalidate caches when javaconfig.json changes
                    }
                }
            }
        };
    }
    
    private void publishDiagnostics(CompilationResult result) {
        List<URI> touched = result.trees
                .stream()
                .map(tree -> tree.getSourceFile().toUri())
                .collect(Collectors.toList());
        Map<URI, PublishDiagnosticsParams> files = new HashMap<>();

        touched.forEach(p -> files.put(p, newPublishDiagnostics(p)));
        
        result.errors.getDiagnostics().forEach(error -> {
            if (error.getStartPosition() != javax.tools.Diagnostic.NOPOS) {
                URI uri = error.getSource().toUri();
                PublishDiagnosticsParams publish = files.computeIfAbsent(uri, this::newPublishDiagnostics);

                Range range = position(error);
                Diagnostic diagnostic = new Diagnostic();
                DiagnosticSeverity severity = severity(error.getKind());

                diagnostic.setSeverity(severity);
                diagnostic.setRange(range);
                diagnostic.setCode(error.getCode());
                diagnostic.setMessage(error.getMessage(null));

                publish.getDiagnostics().add(diagnostic);
            }
        });

        files.values().forEach(d -> client.publishDiagnostics(d));
    }

    private DiagnosticSeverity severity(javax.tools.Diagnostic.Kind kind) {
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

    private PublishDiagnosticsParams newPublishDiagnostics(URI newUri) {
        PublishDiagnosticsParams p = new PublishDiagnosticsParams();

        p.setDiagnostics(new ArrayList<>());
        p.setUri(newUri.toString());

        return p;
    }

    private Map<JavacConfig, JavacHolder> compilerCache = new HashMap<>();

    /**
     * Instead of looking for javaconfig.json and creating a JavacHolder, just use this.
     * For testing.
     */
    private final Optional<JavacHolder> testJavac;

    /**
     * Look for a configuration in a parent directory of uri
     */
    private Optional<JavacHolder> findCompiler(URI uri) {
        if (testJavac.isPresent())
            return testJavac;
        else
            return dir(uri)
                    .flatMap(this::findConfig)
                    .map(this::findCompilerForConfig);
    }

    private static Optional<Path> dir(URI uri) {
        return file(uri).map(path -> path.getParent());
    }

    private static Optional<Path> file(URI uri) {
        if (!uri.getScheme().equals("file"))
            return Optional.empty();
        else
            return Optional.of(Paths.get(uri));
    }

    private JavacHolder findCompilerForConfig(JavacConfig config) {
        if (testJavac.isPresent())
            return testJavac.get();
        else
            return compilerCache.computeIfAbsent(config, this::newJavac);
    }

    private JavacHolder newJavac(JavacConfig c) {
        return JavacHolder.create(c.classPath,
                               c.sourcePath,
                               c.outputDirectory);
    }

    // TODO invalidate cache when VSCode notifies us config file has changed
    private Map<Path, Optional<JavacConfig>> configCache = new HashMap<>();

    private Optional<JavacConfig> findConfig(Path dir) {
        return configCache.computeIfAbsent(dir, this::doFindConfig);
    }

    private Optional<JavacConfig> doFindConfig(Path dir) {
        if (testJavac.isPresent())
            return testJavac.map(j -> new JavacConfig(j.sourcePath, j.classPath, j.outputDirectory));

        while (true) {
            Optional<JavacConfig> found = readIfConfig(dir);

            if (found.isPresent())
                return found;
            else if (workspaceRoot.startsWith(dir))
                return Optional.empty();
            else
                dir = dir.getParent();
        }
    }

    /**
     * If directory contains a config file, for example javaconfig.json or an eclipse project file, read it.
     */
    private Optional<JavacConfig> readIfConfig(Path dir) {
        if (Files.exists(dir.resolve("javaconfig.json"))) {
            JavaConfigJson json = readJavaConfigJson(dir.resolve("javaconfig.json"));
            Set<Path> classPath = json.classPathFile.map(classPathFile -> {
                Path classPathFilePath = dir.resolve(classPathFile);
                return readClassPathFile(classPathFilePath);
            }).orElse(Collections.emptySet());
            Set<Path> sourcePath = json.sourcePath.stream().map(dir::resolve).collect(Collectors.toSet());
            Path outputDirectory = dir.resolve(json.outputDirectory);
            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory);

            return Optional.of(config);
        }
        else if (Files.exists(dir.resolve("pom.xml"))) {
            Path pomXml = dir.resolve("pom.xml");

            // Invoke maven to get classpath
            Set<Path> classPath = buildClassPath(pomXml);

            // Get source directory from pom.xml
            Set<Path> sourcePath = sourceDirectories(pomXml);

            // Use target/javacs
            Path outputDirectory = Paths.get("target/javacs").toAbsolutePath();

            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory);

            return Optional.of(config);
        }
        // TODO add more file types
        else {
            return Optional.empty();
        }
    }

    public static Set<Path> buildClassPath(Path pomXml) {
        try {
            Objects.requireNonNull(pomXml, "pom.xml path is null");

            // Tell maven to output classpath to a temporary file
            // TODO if pom.xml already specifies outputFile, use that location
            Path classPathTxt = Files.createTempFile("classpath", ".txt");

            LOG.info("Emit classpath to " + classPathTxt);

            String cmd = getMvnCommand() + " dependency:build-classpath -Dmdep.outputFile=" + classPathTxt;
            File workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            int result = Runtime.getRuntime().exec(cmd, null, workingDirectory).waitFor();

            if (result != 0)
                throw new RuntimeException("`" + cmd + "` returned " + result);

            return readClassPathFile(classPathTxt);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMvnCommand() {
        String mvnCommand = "mvn";
        if (File.separatorChar == '\\') {
            mvnCommand = findExecutableOnPath("mvn.cmd");
            if (mvnCommand == null) {
                mvnCommand = findExecutableOnPath("mvn.bat");
            }
        }
        return mvnCommand;
    }

    private static String findExecutableOnPath(String name) {
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static Set<Path> sourceDirectories(Path pomXml) {
        try {
            Set<Path> all = new HashSet<>();

            // Parse pom.xml
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomXml.toFile());

            // Find source directory
            String sourceDir = XPathFactory.newInstance().newXPath().compile("/project/build/sourceDirectory").evaluate(doc);

            if (sourceDir == null || sourceDir.isEmpty()) {
                LOG.info("Use default source directory src/main/java");

                sourceDir = "src/main/java";
            }
            else LOG.info("Use source directory from pom.xml " + sourceDir);
            
            all.add(pomXml.resolveSibling(sourceDir).toAbsolutePath());

            // Find test directory
            String testDir = XPathFactory.newInstance().newXPath().compile("/project/build/testSourceDirectory").evaluate(doc);

            if (testDir == null || testDir.isEmpty()) {
                LOG.info("Use default test directory src/test/java");

                testDir = "src/test/java";
            }
            else LOG.info("Use test directory from pom.xml " + testDir);
            
            all.add(pomXml.resolveSibling(testDir).toAbsolutePath());

            return all;
        } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private JavaConfigJson readJavaConfigJson(Path configFile) {
        try {
            return JSON.readValue(configFile.toFile(), JavaConfigJson.class);
        } catch (IOException e) {
            MessageParams message = new MessageParams();

            message.setMessage("Error reading " + configFile);
            message.setType(MessageType.Error);

            throw new ShowMessageException(message, e);
        }
    }

    private static Set<Path> readClassPathFile(Path classPathFilePath) {
        try {
            InputStream in = Files.newInputStream(classPathFilePath);
            String text = new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining());
            Path dir = classPathFilePath.getParent();

            return Arrays.stream(text.split(File.pathSeparator))
                         .map(dir::resolve)
                         .collect(Collectors.toSet());
        } catch (IOException e) {
            MessageParams message = new MessageParams();

            message.setMessage("Error reading " + classPathFilePath);
            message.setType(MessageType.Error);

            throw new ShowMessageException(message, e);
        }
    }

    private Range position(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        // Compute start position
        Position start = new Position();

        start.setLine((int) (error.getLineNumber() - 1));
        start.setCharacter((int) (error.getColumnNumber() - 1));

        // Compute end position
        Position end = endPosition(error);

        // Combine into Range
        Range range = new Range();

        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    private Position endPosition(javax.tools.Diagnostic<? extends JavaFileObject> error) {
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

            Position end = new Position();

            end.setLine(line);
            end.setCharacter(column);

            return end;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<? extends Location> findReferences(ReferenceParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        Optional<String> content = activeContent(uri);
        int line = params.getPosition().getLine();
        int character = params.getPosition().getCharacter();
        long cursor = findOffset(uri, line, character);

        return findCompiler(uri)
                .map(compiler -> compiler.findReferences(uri, content, cursor))
                .orElse(Collections.emptyList());
    }

    public Optional<Symbol> findSymbol(URI file, int line, int character) {
        Optional<String> content = activeContent(file);
        long cursor = findOffset(file, line, character);

        return findCompiler(file).flatMap(compiler -> compiler.symbolAt(file, content, cursor));
    }

    private List<? extends SymbolInformation> findDocumentSymbols(DocumentSymbolParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());

        return findCompiler(uri)
                .map(compiler -> compiler.index.allInFile(uri))
                .orElse(Stream.empty())
                .collect(Collectors.toList());
    }

    public List<? extends Location> gotoDefinition(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        Optional<String> content = activeContent(uri);
        int line = position.getPosition().getLine();
        int character = position.getPosition().getCharacter();
        long cursor = findOffset(uri, line, character);

        return findCompiler(uri)
                .flatMap(compiler -> compiler.gotoDefinition(uri, content, cursor))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    /**
     * Convert on offset-based range to a {@link Range}
     */
    public static Range findPosition(JavaFileObject file, long startOffset, long endOffset) {
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

            Position start = createPosition(line, character);

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

            Position end = createPosition(line, character);

            // Combine into range
            Range range = new Range();

            range.setStart(start);
            range.setEnd(end);

            return range;
        } catch (IOException e) {
            throw ShowMessageException.error(e.getMessage(), e);
        }
    }

    private static Position createPosition(int line, int character) {
        Position p = new Position();

        p.setLine(line);
        p.setCharacter(character);

        return p;
    }

    private static long findOffset(URI file, int targetLine, int targetCharacter) {
        return file(file).map(path -> {
            try (Reader in = Files.newBufferedReader(path)) {
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
        }).orElseThrow(() -> ShowMessageException.error("Can't find " + targetLine + ":" + targetCharacter + " in " + file, null));
    }
    
    private Hover doHover(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        Optional<String> content = activeContent(uri);
        int line = position.getPosition().getLine();
        int character = position.getPosition().getCharacter();
        long cursor = findOffset(uri, line, character);

        return findCompiler(uri)
                .flatMap(compiler -> compiler.symbolAt(uri, content, cursor))
                .flatMap(JavaLanguageServer::hoverText)
                .map(text -> new Hover(Collections.singletonList(text), null))
                .orElse(new Hover());
    }

    private static Optional<String> hoverText(Symbol symbol) {
        switch (symbol.getKind()) {
            case PACKAGE:
                return Optional.of("package " + symbol.getQualifiedName());
            case ENUM:
                return Optional.of("enum " + symbol.getQualifiedName());
            case CLASS:
                return Optional.of("class " + symbol.getQualifiedName());
            case ANNOTATION_TYPE:
                return Optional.of("@interface " + symbol.getQualifiedName());
            case INTERFACE:
                return Optional.of("interface " + symbol.getQualifiedName());
            case METHOD:
            case CONSTRUCTOR:
            case STATIC_INIT:
            case INSTANCE_INIT:
                Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                String signature = AutocompleteVisitor.methodSignature(method);
                String returnType = ShortTypePrinter.print(method.getReturnType());

                return Optional.of(returnType + " " + signature);
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case ENUM_CONSTANT:
            case FIELD:
                return Optional.of(ShortTypePrinter.print(symbol.type));
            case TYPE_PARAMETER:
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                return Optional.empty();
        }
    }

    public CompletionList autocomplete(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        Optional<String> content = activeContent(uri);
        int line = position.getPosition().getLine();
        int character = position.getPosition().getCharacter();
        long cursor = findOffset(uri, line, character);
        List<CompletionItem> items = findCompiler(uri)
                .map(compiler -> compiler.autocomplete(uri, content, cursor))
                .orElse(Collections.emptyList());

        return new CompletionList(false, items);
    }

    public void installClient(LanguageClient client) {
        this.client = client;

        Logger.getLogger("").addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                client.logMessage(new MessageParams(
                        messageType(record.getLevel().intValue()),
                        record.getMessage()
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
}
