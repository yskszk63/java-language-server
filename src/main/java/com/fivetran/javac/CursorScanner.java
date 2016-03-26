package com.fivetran.javac;

import com.fivetran.javac.BaseScanner;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;

public class CursorScanner extends BaseScanner {
    protected final JavaFileObject file;
    protected final long cursor;

    public CursorScanner(JavaFileObject file, long cursor, Context context) {
        super(context);
        this.file = file;
        this.cursor = cursor;
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
