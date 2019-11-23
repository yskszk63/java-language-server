package org.javacs.rewrite;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import java.util.List;

public class CompileTask implements AutoCloseable {
    public final JavacTask task;
    public final List<CompilationUnitTree> roots;
    private final Runnable close;

    public final CompilationUnitTree root() {
        if (roots.size() != 1) {
            throw new RuntimeException(Integer.toString(roots.size()));
        }
        return roots.get(0);
    }

    public CompileTask(JavacTask task, List<CompilationUnitTree> roots, Runnable close) {
        this.task = task;
        this.roots = roots;
        this.close = close;
    }

    @Override
    public void close() {
        close.run();
    }
}
