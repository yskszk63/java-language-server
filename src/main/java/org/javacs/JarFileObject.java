package org.javacs;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

class JarFileObject implements JavaFileObject {
    final Path jar;
    final JarEntry entry;

    JarFileObject(Path jar, JarEntry entry) {
        this.jar = jar;
        this.entry = entry;
    }

    @Override
    public Kind getKind() {
        var name = entry.getName().toString();
        return SourceFileObject.kindFromExtension(name);
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        var relative = Paths.get(simpleName.replace('.', '/'));
        var absolute = Paths.get(entry.getName());
        return absolute.endsWith(relative);
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
        var entryName = entry.getName();
        var jarURI = jar.toUri().normalize();
        var separator = entryName.startsWith("/") ? "!" : "!/";
        return URI.create("jar:" + jarURI + separator + entryName);
    }

    @Override
    public String getName() {
        return jar + "(" + entry.getName() + ")";
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new JarFile(jar.toFile()).getInputStream(entry);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream());
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        var bytes = openInputStream().readAllBytes();
        return new String(bytes, Charset.forName("UTF-8"));
    }

    @Override
    public Writer openWriter() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        return entry.getLastModifiedTime().toMillis();
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object other) {
        if (other.getClass() != JarFileObject.class) return false;
        var that = (JarFileObject) other;
        return this.jar.equals(that.jar) && this.entry.getName().equals(that.entry.getName());
    }
}
