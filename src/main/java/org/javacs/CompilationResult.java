package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.Collection;

class CompilationResult {
    final Collection<CompilationUnitTree> trees;
    final DiagnosticCollector<JavaFileObject> errors;
    final JavacTask task;

    CompilationResult(Collection<CompilationUnitTree> trees, DiagnosticCollector<JavaFileObject> errors, JavacTask task) {
        this.trees = trees;
        this.errors = errors;
        this.task = task;
    }
}
