package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

class Pruner {
    private static final Logger LOG = Logger.getLogger("main");
    // Parse-only compiler
    // TODO this should come from Parser
    private static final JavaCompiler COMPILER = ServiceLoader.load(JavaCompiler.class).iterator().next();
    private static final StandardJavaFileManager FILE_MANAGER =
            COMPILER.getStandardFileManager(Pruner::report, null, Charset.defaultCharset());

    private static void report(Diagnostic<? extends JavaFileObject> diags) {
        LOG.warning(diags.getMessage(null));
    }

    private static JavacTask singleFileTask(URI file, String contents) {
        return (JavacTask)
                COMPILER.getTask(
                        null,
                        FILE_MANAGER,
                        Pruner::report,
                        Arrays.asList("-proc:none", "-g"),
                        Collections.emptyList(),
                        Collections.singletonList(new StringFileObject(contents, file)));
    }

    private final JavacTask task;
    private final CompilationUnitTree root;
    private final StringBuilder contents;

    Pruner(URI file, String contents) {
        this.task = singleFileTask(file, contents);
        try {
            this.root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.contents = new StringBuilder(contents);
    }

    void prune(int line, int character) {
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
                    switch (contents.charAt(i)) {
                        case '\r':
                        case '\n':
                            break;
                        default:
                            contents.setCharAt(i, ' ');
                    }
                }
            }

            @Override
            public Void visitBlock(BlockTree node, Void aVoid) {
                if (containsCursor(node)) {
                    super.visitBlock(node, aVoid);
                    // When we find the deepest block that includes the cursor
                    if (!erasedAfterCursor) {
                        for (StatementTree line : node.getStatements()) {
                            var start = sourcePositions.getStartPosition(root, line);
                            var end = sourcePositions.getEndPosition(root, line);
                            if (cursor < start) erase(start, end);
                        }
                        erasedAfterCursor = true;
                    }
                } else if (!node.getStatements().isEmpty()) {
                    var first = node.getStatements().get(0);
                    var last = node.getStatements().get(node.getStatements().size() - 1);
                    var start = sourcePositions.getStartPosition(root, first);
                    var end = sourcePositions.getEndPosition(root, last);
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
    }

    String contents() {
        return contents.toString();
    }
}
