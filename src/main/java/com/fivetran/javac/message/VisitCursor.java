package com.fivetran.javac.message;

import com.fivetran.javac.BridgeExpressionScanner;
import com.sun.source.tree.Tree;

import javax.tools.JavaFileObject;

public class VisitCursor extends BridgeExpressionScanner {
    protected final JavaFileObject file;
    protected final long cursor;

    public VisitCursor(JavaFileObject file, long cursor) {
        this.file = file;
        this.cursor = cursor;
    }

    @Override
    public void scan(Tree node) {
        if (containsCursor(node))
            super.scan(node);
    }

    protected boolean containsCursor(Tree node) {
        if (!compilationUnit().getSourceFile().equals(file))
            return false;

        long start = trees().getSourcePositions().getStartPosition(compilationUnit(), node);
        long end = trees().getSourcePositions().getEndPosition(compilationUnit(), node);

        return start <= cursor && cursor <= end;
    }
}
