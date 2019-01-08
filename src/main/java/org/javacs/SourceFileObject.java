package org.javacs;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

class SourceFileObject implements JavaFileObject {
    final Path path;

    SourceFileObject(Path path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object other) {
        if (other.getClass() != SourceFileObject.class) return false;
        var that = (SourceFileObject) other;
        return this.path.equals(that.path);
    }

    @Override
    public Kind getKind() {
        var name = path.getFileName().toString();
        return kindFromExtension(name);
    }

    static Kind kindFromExtension(String name) {
        for (var candidate : Kind.values()) {
            if (name.endsWith(candidate.extension)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return path.getFileName().toString().equals(simpleName + kind.extension);
    }

    @Override
    public NestingKind getNestingKind() {
        return null;
    }

    @Override
    public Modifier getAccessLevel() {
        return null;
    }

    @Override
    public URI toUri() {
        return path.toUri();
    }

    @Override
    public String getName() {
        return path.toString();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return FileStore.inputStream(path);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return FileStore.bufferedReader(path);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return FileStore.contents(path);
    }

    @Override
    public Writer openWriter() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        return FileStore.modified(path).toEpochMilli();
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }
}
