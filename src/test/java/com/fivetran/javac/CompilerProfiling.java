package com.fivetran.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.comp.CompileStates;
import org.junit.Ignore;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

@Ignore
public class CompilerProfiling extends Fixtures {
    private static final Logger LOG = Logger.getLogger("");

    @Test
    public void parsingSpeed() throws IOException, URISyntaxException {
        StringFileObject file = fromResource("/LargeFile.java");

        for (int i = 0; i < 10; i++) {
            Duration duration = compileLargeFile(file);

            LOG.info(duration.toString());
        }
    }

    private Duration compileLargeFile(StringFileObject file) {
        long start = System.nanoTime();

        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        GetCompilationUnit compilationUnit = new GetCompilationUnit();
        JavacHolder compiler = new JavacHolder(Collections.emptyList(), Collections.emptyList(), "out");
        compiler.afterParse(compilationUnit);
        compiler.onError(errors);
        try {
            compiler.parse(file);
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

    private StringFileObject fromResource(String file) throws URISyntaxException, IOException {
        Path path = Paths.get(LinterTest.class.getResource(file).toURI());
        String content = new String(Files.readAllBytes(path));
        return new StringFileObject(content, path);
    }

    private static class GetCompilationUnit extends BridgeExpressionScanner {
        private CompilationUnitTree result;

        @Override
        protected void visitCompilationUnit(CompilationUnitTree node) {
            this.result = node;

            throw new AbortCompilation();
        }
    }

    private static class AbortCompilation extends RuntimeException {

    }
}
