package org.javacs;

import java.io.IOException;
import java.net.URI;
import javax.tools.SimpleJavaFileObject;

public class StringFileObject extends SimpleJavaFileObject {
    public final String content;
    public final URI path; // TODO rename

    public StringFileObject(String content, URI path) {
        super(path, Kind.SOURCE);

        this.content = content;
        this.path = path;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return content;
    }
}
