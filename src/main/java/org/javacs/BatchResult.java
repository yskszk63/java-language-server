package org.javacs;

import com.sun.source.util.JavacTask;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

class BatchResult {

    final JavacTask task;

    /**
     * Errors created while compiling files
     */
    final DiagnosticCollector<JavaFileObject> errors;

    BatchResult(JavacTask task, DiagnosticCollector<JavaFileObject> errors) {
        this.task = task;
        this.errors = errors;
    }
}
