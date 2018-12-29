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
    private static final Path largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java")),
            smallFile = Paths.get(FindResource.uri("/org/javacs/example/Goto.java"));
    // "removeMethodBodies" appears late in the file, so stopping early will not be very effective
    private static final String query = "removeMethodBodies";
    /*
        @Benchmark
        public void containsWordMatchingSmall() {
            var found = Parser.containsWordMatching(smallFile, query);
            assert found;
        }

        @Benchmark
        public void containsWordMatchingLarge() {
            var found = Parser.containsWordMatching(largeFile, query);
            assert found;
        }

        @Benchmark
        public void containsTextSmall() {
            var found = Parser.containsText(smallFile, query);
            assert found;
        }

        @Benchmark
        public void containsTextLarge() {
            var found = Parser.containsText(largeFile, query);
            assert found;
        }
    */
    @Benchmark
    public void containsImportLarge() {
        var found = JavaCompilerService.containsImport("java.util.nopkg", "Logger", largeFile);
        assert found;
    }

    @Benchmark
    public void containsImportSmall() {
        var found = JavaCompilerService.containsImport("java.util.nopkg", "Logger", smallFile);
        assert found;
    }

    @Benchmark
    public void containsWordLarge() {
        var found = JavaCompilerService.containsWord("removeMethodBodies", largeFile);
        assert found;
    }

    @Benchmark
    public void containsWordSmall() {
        var found = JavaCompilerService.containsWord("removeMethodBodies", smallFile);
        assert found;
    }

    @Benchmark
    public void parseLarge() {
        var found = Parser.parse(largeFile);
        assert found != null;
    }

    @Benchmark
    public void parseSmall() {
        var found = Parser.parse(smallFile);
        assert found != null;
    }
}
