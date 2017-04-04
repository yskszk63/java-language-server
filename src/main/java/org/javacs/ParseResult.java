package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

class ParseResult {
    final JavacTask task;
    final CompilationUnitTree tree;

    ParseResult(JavacTask task, CompilationUnitTree tree) {
        this.task = task;
        this.tree = tree;
    }
}
