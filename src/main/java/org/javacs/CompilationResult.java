package org.javacs;

import com.sun.tools.javac.tree.JCTree;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.Collection;

class CompilationResult {
    final Collection<JCTree.JCCompilationUnit> trees;
    final DiagnosticCollector<JavaFileObject> errors;

    CompilationResult(Collection<JCTree.JCCompilationUnit> trees, DiagnosticCollector<JavaFileObject> errors) {
        this.trees = trees;
        this.errors = errors;
    }
}
