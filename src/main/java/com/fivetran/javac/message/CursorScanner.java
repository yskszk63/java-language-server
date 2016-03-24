package com.fivetran.javac.message;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;

public class CursorScanner extends TreeScanner {
    protected final JavaFileObject file;
    protected final long cursor;
    protected final Context context;
    protected JCTree.JCCompilationUnit compilationUnit;

    public CursorScanner(JavaFileObject file, long cursor, Context context) {
        this.file = file;
        this.cursor = cursor;
        this.context = context;
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        this.compilationUnit = tree;

        super.visitTopLevel(tree);
    }

    @Override
    public void scan(JCTree tree) {
        if (containsCursor(tree))
            super.scan(tree);
    }

    protected boolean containsCursor(JCTree node) {
        if (!compilationUnit.getSourceFile().equals(file))
            return false;

        JavacTrees trees = JavacTrees.instance(context);
        long start = trees.getSourcePositions().getStartPosition(compilationUnit, node);
        long end = trees.getSourcePositions().getEndPosition(compilationUnit, node);

        return start <= cursor && cursor <= end;
    }
}
