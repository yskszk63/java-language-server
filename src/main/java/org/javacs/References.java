package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import org.eclipse.lsp4j.Location;

public class References {

    public static Stream<Location> findReferences(FocusedResult compiled, SymbolIndex index) {
        return compiled.cursor
                .map(cursor -> new References(compiled.task, index).doFindReferences(cursor))
                .orElseGet(Stream::empty);
    }

    public static Optional<Location> gotoDefinition(FocusedResult compiled, SymbolIndex index) {
        return compiled.cursor.flatMap(
                cursor -> new References(compiled.task, index).doGotoDefinition(cursor));
    }

    private final JavacTask task;
    private final SymbolIndex index;

    private References(JavacTask task, SymbolIndex index) {
        this.task = task;
        this.index = index;
    }

    private Stream<Location> doFindReferences(TreePath cursor) {
        Trees trees = Trees.instance(task);
        Element symbol = trees.getElement(cursor);

        if (symbol == null) return Stream.empty();
        else return index.references(symbol);
    }

    private Optional<Location> doGotoDefinition(TreePath cursor) {
        CursorContext context = CursorContext.from(cursor);

        if (context == CursorContext.NewClass) cursor = context.find(cursor);

        Trees trees = Trees.instance(task);
        Element symbol = trees.getElement(cursor);

        return index.find(symbol);
    }
}
