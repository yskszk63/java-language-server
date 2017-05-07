package org.javacs;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.eclipse.lsp4j.Location;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class References {

    public static Stream<Location> findReferences(FocusedResult compiled, SymbolIndex index) {
        return compiled.cursor
                .map(cursor -> new References(compiled.task, index).doFindReferences(cursor))
                .orElseGet(Stream::empty);
    }

    public static Optional<Location> gotoDefinition(FocusedResult compiled, SymbolIndex index) {
        return compiled.cursor
                .flatMap(cursor -> new References(compiled.task, index).doGotoDefinition(cursor));
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

        if (symbol == null)
            return Stream.empty();

        if (SymbolIndex.shouldIndex(symbol))
            return index.references(symbol);
        else
            return nonIndexedReferences(symbol, trees).stream();
    }

    /**
     * References to things where SymbolIndex.shouldIndex(symbol) is false
     */
    public static List<Location> nonIndexedReferences(final Element symbol, final Trees trees) {
        List<Location> result = new ArrayList<>();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree node, Void aVoid) {
                checkForReference();

                return super.visitIdentifier(node, aVoid);
            }

            private void checkForReference() {
                Element element = trees.getElement(getCurrentPath());

                if (SymbolIndex.sameSymbol(symbol, element))
                    SymbolIndex.findElementName(element, trees).ifPresent(result::add);
            }
        }.scan(trees.getTree(symbol), null);

        return result;
    }

    private Optional<Location> doGotoDefinition(TreePath cursor) {
        CursorContext context = CursorContext.from(cursor);

        if (context == CursorContext.NewClass)
            cursor = context.find(cursor);

        Trees trees = Trees.instance(task);
        Element symbol = trees.getElement(cursor);

        return SymbolIndex.findElementName(symbol, trees);
    }
}
