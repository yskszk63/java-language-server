package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
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

                if (element.equals(symbol))
                    findTree(getCurrentPath(), trees).ifPresent(result::add);
            }
        }.scan(trees.getTree(symbol), null);

        return result;
    }

    private Optional<Location> doGotoDefinition(TreePath cursor) {
        Trees trees = Trees.instance(task);
        Element symbol = trees.getElement(cursor);

        if (SymbolIndex.shouldIndex(symbol))
            return index.find(symbol).map(info -> info.getLocation());
        else {
            TreePath path = trees.getPath(symbol);

            return findTree(path, trees);
        }
    }

    /**
     * Find the location of the tree pointed to by path.
     */
    private static Optional<Location> findTree(TreePath path, Trees trees) {
        if (path == null)
            return Optional.empty();

        CompilationUnitTree compilationUnit = path.getCompilationUnit();
        Tree leaf = path.getLeaf();
        long start = trees.getSourcePositions().getStartPosition(compilationUnit, leaf);
        long end = trees.getSourcePositions().getEndPosition(compilationUnit, leaf);

        if (start != Diagnostic.NOPOS && end != Diagnostic.NOPOS) {
            LineMap lineMap = compilationUnit.getLineMap();

            return Optional.of(new Location(
                    compilationUnit.getSourceFile().toUri().toString(),
                    new Range(
                            new Position((int) lineMap.getLineNumber(start) - 1, (int) lineMap.getColumnNumber(start) - 1),
                            new Position((int) lineMap.getLineNumber(end) - 1, (int) lineMap.getColumnNumber(end) - 1)
                    )
            ));
        }

        return Optional.empty();
    }
}
