package org.javacs;

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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

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
        JavacHolder compiler = JavacHolder.createWithoutIndex(Collections.emptySet(), Collections.emptySet(), Paths.get("out"));
        CompilationResult result = compiler.compile(Collections.singletonMap(file, Optional.empty()));
        long finish = System.nanoTime();

        assertThat(result.trees, not(empty()));

        return Duration.ofNanos(finish - start);
    }
}
