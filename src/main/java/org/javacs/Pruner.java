package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;

class Pruner {
    static String prune(URI file, String contents, int line, int character) {
        var task = Parser.parseTask(new StringFileObject(contents, file));
        CompilationUnitTree root;
        try {
            root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var buffer = new StringBuilder(contents);
        var sourcePositions = Trees.instance(task).getSourcePositions();
        var lines = root.getLineMap();
        var cursor = lines.getPosition(line, character);

        class Scan extends TreeScanner<Void, Void> {
            boolean erasedAfterCursor = false;

            boolean containsCursor(Tree node) {
                long start = sourcePositions.getStartPosition(root, node),
                        end = sourcePositions.getEndPosition(root, node);
                return start <= cursor && cursor <= end;
            }

            void erase(long start, long end) {
                for (int i = (int) start; i < end; i++) {
                    switch (buffer.charAt(i)) {
                        case '\r':
                        case '\n':
                            break;
                        default:
                            buffer.setCharAt(i, ' ');
                    }
                }
            }

            @Override
            public Void visitImport(ImportTree node, Void aVoid) {
                // Erase 'static' keyword so autocomplete works better
                if (containsCursor(node) && node.isStatic()) {
                    var start = (int) sourcePositions.getStartPosition(root, node);
                    start = buffer.indexOf("static", start);
                    var end = start + "static".length();
                    erase(start, end);
                }

                return super.visitImport(node, aVoid);
            }

            @Override
            public Void visitBlock(BlockTree node, Void aVoid) {
                if (containsCursor(node)) {
                    super.visitBlock(node, aVoid);
                    // When we find the deepest block that includes the cursor
                    if (!erasedAfterCursor) {
                        var start = cursor;
                        var end = sourcePositions.getEndPosition(root, node);
                        if (end >= buffer.length()) end = buffer.length() - 1;
                        // Find the next line
                        while (start < end && buffer.charAt((int) start) != '\n') start++;
                        // Find the end of the block
                        while (end > start && buffer.charAt((int) end) != '}') end--;
                        // Erase from next line to end of block
                        erase(start, end - 1);
                        erasedAfterCursor = true;
                    }
                } else if (!node.getStatements().isEmpty()) {
                    var first = node.getStatements().get(0);
                    var last = node.getStatements().get(node.getStatements().size() - 1);
                    var start = sourcePositions.getStartPosition(root, first);
                    var end = sourcePositions.getEndPosition(root, last);
                    if (end >= buffer.length()) end = buffer.length() - 1;
                    erase(start, end);
                }
                return null;
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void nothing) {
                return super.scan(node.getErrorTrees(), nothing);
            }
        }

        new Scan().scan(root, null);

        return buffer.toString();
    }
}
