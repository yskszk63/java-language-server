package org.javacs.lsp;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LSP {
    private static final Gson gson = new Gson();

    private static String readHeader(InputStream client) {
        var line = new StringBuilder();
        for (var next = read(client); next != -1; next = read(client)) {
            if (next == '\r') {
                var last = read(client);
                assert last == '\n';
                break;
            }
            line.append((char) next);
        }
        return line.toString();
    }

    private static int parseHeader(String header) {
        var contentLength = "Content-Length: ";
        if (header.startsWith(contentLength)) {
            var tail = header.substring(contentLength.length());
            var length = Integer.parseInt(tail);
            return length;
        }
        return -1;
    }

    private static int read(InputStream client) {
        try {
            return client.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readLength(InputStream client, int byteLength) {
        // Eat whitespace
        // Have observed problems with extra \r\n sequences from VSCode
        var next = read(client);
        while (next != -1 && Character.isWhitespace(next)) {
            next = read(client);
        }
        // Append next
        var result = new StringBuilder();
        var i = 0;
        while (true) {
            if (next == -1) break;
            result.append((char) next);
            i++;
            if (i == byteLength) break;
            next = read(client);
        }
        return result.toString();
    }

    static String nextToken(InputStream client) {
        var contentLength = -1;
        while (true) {
            var line = readHeader(client);
            // If header is empty, next line is the start of the message
            if (line.isEmpty()) return readLength(client, contentLength);
            // If header contains length, save it
            var maybeLength = parseHeader(line);
            if (maybeLength != -1) contentLength = maybeLength;
        }
    }

    static Message parseMessage(String token) {
        return gson.fromJson(token, Message.class);
    }

    private static void writeClient(OutputStream client, String messageText) {
        var messageBytes = messageText.getBytes(Charsets.UTF_8);
        var headerText = String.format("Content-Length: %d\r\n\r\n", messageBytes.length);
        var headerBytes = headerText.getBytes(Charsets.UTF_8);
        try {
            client.write(headerBytes);
            client.write(messageBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void respond(OutputStream client, int requestId, Object params) {
        var jsonText = gson.toJson(params);
        var messageText = String.format("{\"jsonrpc\":\"2.0\",\"id\":%d,\"result\":%s}", requestId, jsonText);
        writeClient(client, messageText);
    }

    private static void notifyClient(OutputStream client, String method, Object params) {
        var jsonText = gson.toJson(params);
        var messageText = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":%s}", method, jsonText);
        writeClient(client, messageText);
    }

    private static class RealClient implements LanguageClient {
        final OutputStream send;

        RealClient(OutputStream send) {
            this.send = send;
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams params) {
            var json = gson.toJson(params);
            notifyClient(send, "textDocument/publishDiagnostics", json);
        }

        @Override
        public void showMessage(ShowMessageParams params) {
            var json = gson.toJson(params);
            notifyClient(send, "window/showMessage", json);
        }

        @Override
        public void registerCapability(String id, JsonElement options) {
            var p = new RegistrationParams();
            p.id = UUID.randomUUID().toString();
            p.id = id;
            p.registerOptions = options;
            var json = gson.toJson(p);
            notifyClient(send, "client/registerCapability", json);
        }

        @Override
        public void customNotification(String method, JsonElement params) {
            var json = gson.toJson(params);
            notifyClient(send, method, json);
        }
    }

    public static void connect(
            Function<LanguageClient, LanguageServer> serverFactory, InputStream receive, OutputStream send) {
        var server = serverFactory.apply(new RealClient(send));
        var pendingRequests = new ArrayBlockingQueue<Message>(10);

        // Read messages and process cancellations on a separate thread
        class MessageReader implements Runnable {
            void peek(Message message) {
                if (message.method.equals("$/cancelRequest")) {
                    var params = gson.fromJson(message.params, CancelParams.class);
                    var removed = pendingRequests.removeIf(r -> r.id.equals(params.id));
                    if (removed) LOG.info(String.format("Cancelled request %d, which had not yet started", params.id));
                    else LOG.info(String.format("Cannot cancel request %d because it has already started", params.id));
                }
            }

            @Override
            public void run() {
                LOG.info("Placing incoming messages on queue...");

                while (true) {
                    try {
                        var token = nextToken(receive);
                        var message = parseMessage(token);
                        peek(message);
                        pendingRequests.put(message);
                        LOG.info(
                                String.format(
                                        "Added message %d to queue, length is %d", message.id, pendingRequests.size()));
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
        }
        Thread reader = new Thread(new MessageReader(), "reader");
        reader.setDaemon(true);
        reader.start();

        // Process messages on main thread
        LOG.info("Reading messages from queue...");
        processMessages:
        while (true) {
            try {
                var request = pendingRequests.take();
                LOG.info(String.format("Read message %d from queue, length is %d", request.id, pendingRequests.size()));
                switch (request.method) {
                    case "initialize":
                        {
                            var params = gson.fromJson(request.params, InitializeParams.class);
                            var response = server.initialize(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "initialized":
                        {
                            server.initialized();
                            break;
                        }
                    case "shutdown":
                        {
                            LOG.warning("Got shutdown message");
                            break;
                        }
                    case "exit":
                        {
                            LOG.warning("Got exit message, exiting...");
                            break processMessages;
                        }
                    case "workspace/didChangeWorkspaceFolders":
                        {
                            var params = gson.fromJson(request.params, DidChangeWorkspaceFoldersParams.class);
                            server.didChangeWorkspaceFolders(params);
                            break;
                        }
                    case "workspace/didChangeConfiguration":
                        {
                            var params = gson.fromJson(request.params, DidChangeConfigurationParams.class);
                            server.didChangeConfiguration(params);
                            break;
                        }
                    case "workspace/didChangeWatchedFiles":
                        {
                            var params = gson.fromJson(request.params, DidChangeWatchedFilesParams.class);
                            server.didChangeWatchedFiles(params);
                            break;
                        }
                    case "workspace/symbols":
                        {
                            var params = gson.fromJson(request.params, WorkspaceSymbolParams.class);
                            var response = server.workspaceSymbols(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/didOpen":
                        {
                            var params = gson.fromJson(request.params, DidOpenTextDocumentParams.class);
                            server.didOpenTextDocument(params);
                            break;
                        }
                    case "textDocument/didChange":
                        {
                            var params = gson.fromJson(request.params, DidChangeTextDocumentParams.class);
                            server.didChangeTextDocument(params);
                            break;
                        }
                    case "textDocument/willSave":
                        {
                            var params = gson.fromJson(request.params, WillSaveTextDocumentParams.class);
                            server.willSaveTextDocument(params);
                            break;
                        }
                    case "textDocument/willSaveWaitUntil":
                        {
                            var params = gson.fromJson(request.params, WillSaveTextDocumentParams.class);
                            var response = server.willSaveWaitUntilTextDocument(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/didSave":
                        {
                            var params = gson.fromJson(request.params, DidSaveTextDocumentParams.class);
                            server.didSaveTextDocument(params);
                            break;
                        }
                    case "textDocument/didClose":
                        {
                            var params = gson.fromJson(request.params, DidCloseTextDocumentParams.class);
                            server.didCloseTextDocument(params);
                            break;
                        }
                    case "textDocument/completion":
                        {
                            var params = gson.fromJson(request.params, CompletionParams.class);
                            var response = server.completion(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "completionItem/resolve":
                        {
                            var params = gson.fromJson(request.params, CompletionItem.class);
                            var response = server.resolveCompletionItem(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/hover":
                        {
                            var params = gson.fromJson(request.params, TextDocumentPositionParams.class);
                            var response = server.hover(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/signatureHelp":
                        {
                            var params = gson.fromJson(request.params, TextDocumentPositionParams.class);
                            var response = server.signatureHelp(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/definition":
                        {
                            var params = gson.fromJson(request.params, TextDocumentPositionParams.class);
                            var response = server.gotoDefinition(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/references":
                        {
                            var params = gson.fromJson(request.params, ReferenceParams.class);
                            var response = server.findReferences(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/documentSymbol":
                        {
                            var params = gson.fromJson(request.params, DocumentSymbolParams.class);
                            var response = server.documentSymbol(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/codeAction":
                        {
                            var params = gson.fromJson(request.params, CodeActionParams.class);
                            var response = server.codeAction(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/codeLens":
                        {
                            var params = gson.fromJson(request.params, CodeLensParams.class);
                            var response = server.codeLens(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "codeLens/resolve":
                        {
                            var params = gson.fromJson(request.params, CodeLens.class);
                            var response = server.resolveCodeLens(params);
                            respond(send, request.id, response);
                            break;
                        }
                    case "textDocument/rename":
                        {
                            var params = gson.fromJson(request.params, RenameParams.class);
                            var response = server.rename(params);
                            respond(send, request.id, response);
                            break;
                        }
                    default:
                        LOG.warning(String.format("Don't know what to do with method `%s`", request.method));
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
            }
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
