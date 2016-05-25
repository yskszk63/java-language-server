package org.javacs;

import io.typefox.lsapi.*;
import io.typefox.lsapi.json.LanguageServerToJsonAdapter;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOG = Logger.getLogger("main");

    public static void main(String[] args) throws IOException {
        LoggingFormat.startLogging();

        Connection connection = connectToNode();

        run(connection);
    }

    private static Connection connectToNode() throws IOException {
        String port = System.getProperty("javacs.port");

        if (port != null) {
            Socket socket = new Socket("localhost", Integer.parseInt(port));

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            OutputStream intercept = new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    out.write(b);
                }
            };

            LOG.info("Connected to parent using socket on port " + port);

            return new Connection(in, intercept);
        }
        else {
            InputStream in = System.in;
            PrintStream out = System.out;

            LOG.info("Connected to parent using stdio");

            return new Connection(in, out);
        }
    }

    private static class Connection {
        final InputStream in;
        final OutputStream out;

        private Connection(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }
    }

    /**
     * Listen for requests from the parent node process.
     * Send replies asynchronously.
     * When the request stream is closed, wait for 5s for all outstanding responses to compute, then return.
     */
    public static void run(Connection connection) {
        LanguageServer server = new JavaLanguageServer();

        LanguageServerToJsonAdapter jsonServer = new LanguageServerToJsonAdapter(server);

        jsonServer.connect(connection.in, connection.out);
        jsonServer.getProtocol().addErrorListener((message, err) -> {
            LOG.log(Level.SEVERE, message, err);
        });

        jsonServer.start();
        jsonServer.join();
    }

    //
//    public ResponseAutocomplete autocomplete(RequestAutocomplete request) throws IOException {
//        Path path = Paths.get(request.path);
//        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
//        StringFileObject file = new StringFileObject(request.text, path);
//        LineMap lines = LineMap.fromString(request.text);
//        long cursor = lines.offset(request.position.line, request.position.character);
//        AutocompleteVisitor autocompleter = new AutocompleteVisitor(file, cursor, compiler.context);
//
//        compiler.afterAnalyze(autocompleter);
//        compiler.onError(errors);
//        compiler.compile(compiler.parse(file));
//
//        for (Diagnostic<? extends JavaFileObject> error : errors.getDiagnostics()) {
//            LOG.warning(error.toString());
//        }
//
//        return new ResponseAutocomplete(autocompleter.suggestions);
//    }
//
//    public ResponseGoto doGoto(RequestGoto request) throws IOException {
//        Path path = Paths.get(request.path);
//        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
//        StringFileObject file = new StringFileObject(request.text, path);
//        LineMap lines = LineMap.fromString(request.text);
//        long cursor = lines.offset(request.position.line, request.position.character);
//        GotoDefinitionVisitor visitor = new GotoDefinitionVisitor(file, cursor, compiler.context);
//
//        compiler.afterAnalyze(visitor);
//        compiler.onError(errors);
//        compiler.compile(compiler.parse(file));
//
//        ResponseGoto response = new ResponseGoto();
//
//        for (SymbolLocation locate : visitor.definitions) {
//            URI uri = locate.file.toUri();
//            Path symbolPath = Paths.get(uri);
//            // If this is the currently open file, use text
//            // Otherwise use file on disk
//            LineMap symbolLineMap = path.equals(symbolPath) ? lines : LineMap.fromPath(symbolPath);
//            Position start = symbolLineMap.point(locate.startPosition);
//            Position end = symbolLineMap.point(locate.endPosition);
//            Range range = new Range(start, end);
//            Location location = new Location(uri, range);
//
//            response.definitions.add(location);
//        }
//
//        return response;
//    }
//
//    public JsonNode echo(JsonNode echo) {
//        return echo;
//    }
//
//    public ResponseLint lint(RequestLint request) throws IOException {
//        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
//        Path path = Paths.get(request.path);
//        JavaFileObject file = compiler.fileManager.getRegularFile(path.toFile());
//
//        compiler.onError(errors);
//        compiler.compile(compiler.parse(file));
//
//        ResponseLint response = new ResponseLint();
//
//        for (Diagnostic<? extends JavaFileObject> error : errors.getDiagnostics()) {
//            Range range = position(error);
//            String lintPath = error.getSource().toUri().getPath();
//            LintMessage message = new LintMessage(range,
//                                                  error.getMessage(null),
//                                                  LintMessage.Type.Error);
//            List<LintMessage> ms = response.messages.computeIfAbsent(lintPath, newPath -> new ArrayList<>());
//
//            ms.add(message);
//        }
//
//        return response;
//    }
//
//    private Range position(Diagnostic<? extends JavaFileObject> error) {
//        if (error.getStartPosition() == Diagnostic.NOPOS)
//            return Range.NONE;
//
//        Position start = new Position((int) error.getLineNumber() - 1, (int) error.getColumnNumber() - 1);
//        Position end = endPosition(error);
//
//        return new Range(start, end);
//    }
//
//    private Position endPosition(Diagnostic<? extends JavaFileObject> error) {
//        try {
//            Reader reader = error.getSource().openReader(true);
//            long startOffset = error.getStartPosition();
//            long endOffset = error.getEndPosition();
//
//            reader.skip(startOffset);
//
//            int line = (int) error.getLineNumber() - 1;
//            int column = (int) error.getColumnNumber() - 1;
//
//            for (long i = startOffset; i < endOffset; i++) {
//                int next = reader.read();
//
//                if (next == '\n') {
//                    line++;
//                    column = 0;
//                }
//                else
//                    column++;
//            }
//
//            return new Position(line, column);
//        } catch (IOException e) {
//            throw new UncheckedIOException(e);
//        }
//    }
}
