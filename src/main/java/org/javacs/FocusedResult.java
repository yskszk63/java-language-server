package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;

import java.util.Optional;

class FocusedResult {
    final Optional<TreePath> cursor;
    final JavacTask task;
    final ClassPathIndex classPath;
    final SymbolIndex sourcePath;

    FocusedResult(Optional<TreePath> cursor, JavacTask task, ClassPathIndex classPath, SymbolIndex sourcePath) {
        this.cursor = cursor;
        this.task = task;
        this.classPath = classPath;
        this.sourcePath = sourcePath;
    }
}
