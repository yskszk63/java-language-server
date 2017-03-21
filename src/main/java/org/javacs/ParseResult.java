package org.javacs;

import com.sun.tools.javac.tree.JCTree;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

class ParseResult {
    final JCTree.JCCompilationUnit tree;
    final DiagnosticCollector<JavaFileObject> errors;

    ParseResult(JCTree.JCCompilationUnit tree, DiagnosticCollector<JavaFileObject> errors) {
        this.tree = tree;
        this.errors = errors;
    }
}
