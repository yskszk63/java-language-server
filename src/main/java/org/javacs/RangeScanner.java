package org.javacs;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.Optional;

public class RangeScanner extends BaseScanner {
    protected final JavaFileObject file;
    protected final long findStart, findEnd;
    protected Optional<JCTree> found = Optional.empty();

    public RangeScanner(JavaFileObject file, long start, long end, Context context) {
        super(context);
        this.file = file;
        this.findStart = start;
        this.findEnd = end;
    }

    public Optional<JCTree> findIn(JCTree.JCCompilationUnit topLevel) {
        found = Optional.empty();

        topLevel.accept(this);

        return found;
    }

    @Override
    public void scan(JCTree tree) {
        if (containsRange(tree)) {
            found = Optional.of(tree);

            super.scan(tree);
        }
    }

    protected boolean containsRange(JCTree node) {
        JavaFileObject nodeFile = compilationUnit.getSourceFile();

        if (!nodeFile.equals(file))
            return false;

        JavacTrees trees = JavacTrees.instance(context);
        long start = trees.getSourcePositions().getStartPosition(compilationUnit, node);
        long end = trees.getSourcePositions().getEndPosition(compilationUnit, node);

        return start <= findEnd && findStart <= end;
    }
}
