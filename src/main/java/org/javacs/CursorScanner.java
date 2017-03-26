package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class CursorScanner<Result> extends TreePathScanner<Result, Void> implements Function<CompilationUnitTree, Optional<Result>> {
    protected final SourcePositions sourcePositions;
    protected final int line, column;
    protected CompilationUnitTree compilationUnit;
    protected long offset;

    /**
     * Instead of exposing a public constructor, implementing classes should provide a
     * create(task, line, column) factory method that returns {@code Function<CompilationUnitTree, Optional<Result>>}
     * in order to hide the complexity of implementation
     */
    protected CursorScanner(JavacTask task, int line, int column) {
        this.sourcePositions = Trees.instance(task).getSourcePositions();
        this.line = line;
        this.column = column;
    }

    @Override
    public Optional<Result> apply(CompilationUnitTree compilationUnit) {
        this.compilationUnit = compilationUnit;
        this.offset = compilationUnit.getLineMap().getPosition(line, column);

        return Optional.ofNullable(scan(compilationUnit, null));
    }

    @Override
    public Result reduce(Result left, Result right) {
        if (left != null)
            return left;
        else
            return right;
    }

    protected boolean containsCursor(Tree leaf) {
        Objects.requireNonNull(compilationUnit, "You should invoke scan(CompilationUnitTree) rather than scan(Tree, Void) or Tree.accept(CursorScanner, Void)");

        long start = sourcePositions.getStartPosition(compilationUnit, leaf);
        long end = sourcePositions.getEndPosition(compilationUnit, leaf);

        return start <= offset && offset <= end;
    }
}
