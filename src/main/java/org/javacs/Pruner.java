package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.regex.Pattern;

class Pruner {
    private static String prune(CompilationUnitTree root, SourcePositions pos, StringBuilder buffer, long[] offsets) {

        class Scan extends TreeScanner<Void, Void> {
            boolean erasedAfterCursor = false;

            boolean containsCursor(Tree node) {
                var start = pos.getStartPosition(root, node);
                var end = pos.getEndPosition(root, node);
                for (var cursor : offsets) {
                    if (start <= cursor && cursor <= end) {
                        return true;
                    }
                }
                return false;
            }

            long lastCursorIn(Tree node) {
                var start = pos.getStartPosition(root, node);
                var end = pos.getEndPosition(root, node);
                long last = -1;
                for (var cursor : offsets) {
                    if (start <= cursor && cursor <= end) {
                        last = cursor;
                    }
                }
                if (last == -1) {
                    throw new RuntimeException(
                            String.format("No cursor in %s is between %d and %d", offsets, start, end));
                }
                return last;
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
            public Void visitImport(ImportTree node, Void __) {
                // Erase 'static' keyword so autocomplete works better
                if (containsCursor(node) && node.isStatic()) {
                    var start = (int) pos.getStartPosition(root, node);
                    start = buffer.indexOf("static", start);
                    var end = start + "static".length();
                    erase(start, end);
                }

                return super.visitImport(node, null);
            }

            @Override
            public Void visitBlock(BlockTree node, Void __) {
                if (containsCursor(node)) {
                    super.visitBlock(node, null);
                    // When we find the deepest block that includes the cursor
                    if (!erasedAfterCursor) {
                        var cursor = lastCursorIn(node);
                        var start = cursor;
                        var end = pos.getEndPosition(root, node);
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
                    var start = pos.getStartPosition(root, first);
                    var end = pos.getEndPosition(root, last);
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

    static String prune(URI file, int line, int character) {
        // Parse file
        var contents = FileStore.contents(file);
        var task = Parser.parseTask(new SourceFileObject(file, contents));
        CompilationUnitTree root;
        try {
            root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Erase all blocks that don't include line:character
        var lines = root.getLineMap();
        var cursor = lines.getPosition(line, character);
        var pos = Trees.instance(task).getSourcePositions();
        var buffer = new StringBuilder(contents);
        return prune(root, pos, buffer, new long[] {cursor});
    }

    static String prune(URI file, String name) {
        // Find all occurrences of name in contents
        var contents = FileStore.contents(file);
        var list = new ArrayList<Long>();
        var pattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
        var matcher = pattern.matcher(contents);
        while (matcher.find()) {
            list.add((long) matcher.start());
        }
        var offsets = new long[list.size()];
        for (var i = 0; i < list.size(); i++) {
            offsets[i] = list.get(i);
        }
        // Parse file
        var task = Parser.parseTask(new SourceFileObject(file, contents));
        CompilationUnitTree root;
        try {
            root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Erase all blocks that don't contain name
        var buffer = new StringBuilder(contents);
        var pos = Trees.instance(task).getSourcePositions();
        return prune(root, pos, buffer, offsets);
    }
}
