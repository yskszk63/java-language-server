package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

class BatchResult {

    final JavacTask task;

    /**
     * Because batch compilation automatically recompiles all dependent classes,
     * there may be more files here than you requested.
     */
    final Iterable<? extends CompilationUnitTree> trees;

    /**
     * Errors created while compiling files
     */
    final DiagnosticCollector<JavaFileObject> errors;

    BatchResult(JavacTask task, Iterable<? extends CompilationUnitTree> trees, DiagnosticCollector<JavaFileObject> errors) {
        this.task = task;
        this.trees = trees;
        this.errors = errors;
    }
}
