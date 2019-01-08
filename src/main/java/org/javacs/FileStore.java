package org.javacs;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.javacs.lsp.DidChangeTextDocumentParams;
import org.javacs.lsp.DidCloseTextDocumentParams;
import org.javacs.lsp.DidOpenTextDocumentParams;
import org.javacs.lsp.TextDocumentContentChangeEvent;

class FileStore {

    private static final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    /**
     * modified[file] is the modified time of file. modified contains .java files and directories on the source path. If
     * modified contains a directory, it contains all the .java files in that directory. modified is sorted, so you can
     * list all the .java files in a directory by doing modified.tailSet(directory)
     */
    private static final TreeMap<Path, Instant> modified = new TreeMap<>();

    /** Needed in tests */
    static void reset() {
        modified.clear();
        activeDocuments.clear();
    }

    static List<Path> list(Path dir, String packageName) {
        // If we've never indexed dir, index it now
        if (!modified.containsKey(dir)) {
            LOG.info(String.format("Indexing %s for the first time", dir));
            try {
                modified.put(dir, Instant.EPOCH);
                Files.walk(dir).filter(SourcePath::isJavaFile).forEach(FileStore::readModifiedFromDisk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // Walk through a section of index, starting at dir and ending when we find the next dir
        var afterDir = modified.tailMap(dir, false).keySet();
        var packageDir = dir.resolve(packageName.replace('.', File.separatorChar));
        var list = new ArrayList<Path>();
        for (var file : afterDir) {
            if (!SourcePath.isJavaFile(file)) continue;
            if (!file.startsWith(dir)) break;
            if (file.startsWith(packageDir)) list.add(file);
        }
        return list;
    }

    static Instant modified(Path file) {
        // If we've never checked before, look up modified time on disk
        if (!modified.containsKey(file)) {
            readModifiedFromDisk(file);
        }

        // Look up modified time from cache
        return modified.get(file);
    }

    static void externalCreate(Path file) {
        readModifiedFromDisk(file);
    }

    static void externalChange(Path file) {
        readModifiedFromDisk(file);
    }

    static void externalDelete(Path file) {
        modified.remove(file);
    }

    private static void readModifiedFromDisk(Path file) {
        try {
            var time = Files.getLastModifiedTime(file).toInstant();
            modified.put(file, time);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void open(DidOpenTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (!SourcePath.isJavaFile(uri)) return;
        activeDocuments.put(uri, new VersionedContent(document.text, document.version));
    }

    static void change(DidChangeTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (SourcePath.isJavaFile(uri)) {
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
        if (SourcePath.isJavaFile(uri)) {
            // Remove from source cache
            activeDocuments.remove(uri);
        }
    }

    static Set<URI> activeDocuments() {
        return activeDocuments.keySet();
    }

    static int version(URI file) {
        if (!activeDocuments.containsKey(file)) return -1;
        return activeDocuments.get(file).version;
    }

    static String contents(URI file) {
        if (!SourcePath.isJavaFile(file)) {
            LOG.warning("Ignoring non-java file " + file);
            return "";
        }
        if (activeDocuments.containsKey(file)) {
            return activeDocuments.get(file).content;
        }
        try {
            // TODO I think there is a faster path here
            return Files.readAllLines(Paths.get(file)).stream().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String contents(Path file) {
        return contents(file.toUri());
    }

    static InputStream inputStream(Path file) {
        var uri = file.toUri();
        if (activeDocuments.containsKey(uri)) {
            var string = activeDocuments.get(uri).content;
            var bytes = string.getBytes();
            return new ByteArrayInputStream(bytes);
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static BufferedReader bufferedReader(Path file) {
        var uri = file.toUri();
        if (activeDocuments.containsKey(uri)) {
            var string = activeDocuments.get(uri).content;
            return new BufferedReader(new StringReader(string));
        }
        try {
            return Files.newBufferedReader(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static BufferedReader lines(Path file) {
        try {
            return Files.newBufferedReader(file);
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
