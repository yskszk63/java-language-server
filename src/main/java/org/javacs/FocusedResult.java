package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;

import java.util.Optional;

public class FocusedResult {
    public final Optional<TreePath> cursor;
    public final JavacTask task;

    public FocusedResult(Optional<TreePath> cursor, JavacTask task) {
        this.cursor = cursor;
        this.task = task;
    }
}
