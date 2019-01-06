package org.javacs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.javacs.lsp.DidChangeTextDocumentParams;
import org.javacs.lsp.DidCloseTextDocumentParams;
import org.javacs.lsp.DidOpenTextDocumentParams;
import org.javacs.lsp.TextDocumentContentChangeEvent;

class FileStore {

    private static final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    static boolean isJavaFile(URI uri) {
        return uri.getScheme().equals("file") && uri.getPath().endsWith(".java");
    }

    static void open(DidOpenTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (!isJavaFile(uri)) return;
        activeDocuments.put(uri, new VersionedContent(document.text, document.version));
    }

    static void change(DidChangeTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (isJavaFile(uri)) {
            var existing = activeDocuments.get(uri);
            var newText = existing.content;

            if (document.version > existing.version) {
                for (var change : params.contentChanges) {
                    if (change.range == null) newText = change.text;
                    else newText = patch(newText, change);
                }

                activeDocuments.put(uri, new VersionedContent(newText, document.version));
            } else LOG.warning("Ignored change with version " + document.version + " <= " + existing.version);
        }
    }

    static void close(DidCloseTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (isJavaFile(uri)) {
            // Remove from source cache
            activeDocuments.remove(uri);
        }
    }

    static Set<URI> activeDocuments() {
        return activeDocuments.keySet();
    }

    static int version(URI openFile) {
        if (!activeDocuments.containsKey(openFile)) return -1;
        return activeDocuments.get(openFile).version;
    }

    static String contents(URI openFile) {
        if (!isJavaFile(openFile)) {
            LOG.warning("Ignoring non-java file " + openFile);
            return "";
        }
        if (activeDocuments.containsKey(openFile)) {
            return activeDocuments.get(openFile).content;
        }
        try {
            return Files.readAllLines(Paths.get(openFile)).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            var range = change.range;
            var reader = new BufferedReader(new StringReader(sourceText));
            var writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.start.line) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.start.character; character++) writer.write(reader.read());

            // Write replacement text
            writer.write(change.text);

            // Skip replaced text
            reader.skip(change.rangeLength);

            // Write remaining text
            while (true) {
                int next = reader.read();

                if (next == -1) return writer.toString();
                else writer.write(next);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
