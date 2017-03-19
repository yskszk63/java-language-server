package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

@Ignore
public class CompilerProfiling {
    private static final Logger LOG = Logger.getLogger("main");

    @Test
    public void parsingSpeed() throws IOException, URISyntaxException {
        URI file = FindResource.uri("/org/javacs/example/LargeFile.java");

        for (int i = 0; i < 10; i++) {
            Duration duration = compileLargeFile(file);

            LOG.info(duration.toString());
        }
    }

    private Duration compileLargeFile(URI file) {
        long start = System.nanoTime();

        JavacHolder compiler = new JavacHolder(Collections.emptySet(), Collections.emptySet(), Paths.get("out"), false);
        GetCompilationUnit compilationUnit = new GetCompilationUnit(compiler.context);

        try {
            compiler.compile(Collections.singletonMap(file, Optional.empty())).trees.forEach(tree -> tree.accept(compilationUnit));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof AbortCompilation)
                LOG.info("Aborted further stages");
            else
                throw e;
        }

        long finish = System.nanoTime();

        assertNotNull(compilationUnit.result);

        return Duration.ofNanos(finish - start);
    }

    private static class GetCompilationUnit extends BaseScanner {
        private CompilationUnitTree result;

        private GetCompilationUnit(Context context) {
            super(context);
        }

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            this.result = tree;

            throw new AbortCompilation();
        }
    }

    private static class AbortCompilation extends RuntimeException {

    }
}
