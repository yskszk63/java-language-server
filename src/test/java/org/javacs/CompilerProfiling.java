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
        JavacHolder compiler = JavacHolder.createWithoutIndex(Collections.emptySet(), Collections.emptySet(), Paths.get("target/test-output"));
        BatchResult result = compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
        long finish = System.nanoTime();

        return Duration.ofNanos(finish - start);
    }
}
