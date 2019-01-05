package org.javacs;

import com.sun.source.tree.LineMap;
import com.sun.source.util.*;
import java.util.Locale;
import javax.tools.*;
import javax.tools.JavaFileObject;

class Warning implements Diagnostic<JavaFileObject> {

    private final JavaFileObject source;
    private final LineMap lines;
    private final long start, end;
    private final Diagnostic.Kind kind;
    private final String code, message;

    Warning(JavacTask task, TreePath path, Diagnostic.Kind kind, String code, String message) {
        this.source = path.getCompilationUnit().getSourceFile();
        this.lines = path.getCompilationUnit().getLineMap();
        var pos = Trees.instance(task).getSourcePositions();
        this.start = pos.getStartPosition(path.getCompilationUnit(), path.getLeaf());
        this.end = pos.getEndPosition(path.getCompilationUnit(), path.getLeaf());
        this.kind = kind;
        this.code = code;
        this.message = message;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public JavaFileObject getSource() {
        return source;
    }

    @Override
    public long getPosition() {
        return start;
    }

    @Override
    public long getStartPosition() {
        return start;
    }

    @Override
    public long getEndPosition() {
        return end;
    }

    @Override
    public long getLineNumber() {
        return lines.getLineNumber(start);
    }

    @Override
    public long getColumnNumber() {
        return lines.getColumnNumber(start);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage(Locale locale) {
        return message;
    }
}
