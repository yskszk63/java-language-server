package org.javacs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

// TODO this is coloring badly
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class StringSearchBenchmark {
    private static final Path largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java"));
    // "removeMethodBodies" appears late in the file, so stopping early will not be very effective
    private static final String query = "removeMethodBodies";

    @Benchmark
    public void regex() {
        var found = Parser.containsWordMatching(largeFile, query);
        assert found;
    }

    @Benchmark
    public void boyerMoore() {
        var found = Parser.containsText(largeFile, query);
        assert found;
    }
}
