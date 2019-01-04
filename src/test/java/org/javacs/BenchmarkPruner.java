package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BenchmarkPruner {
    private static Path sourceRoot = Paths.get("src/main/java").normalize();
    private static List<StringFileObject> files = files(false);
    private static List<StringFileObject> pruned = files(true);

    private static List<StringFileObject> files(boolean prune) {
        try {
            var files = new ArrayList<StringFileObject>();
            var dir = Paths.get("src/main/java/org/javacs").normalize();
            var it = Files.list(dir).iterator();
            while (it.hasNext()) {
                var file = it.next();
                if (!Files.isRegularFile(file)) continue;
                var contents = String.join("\n", Files.readAllLines(file));
                ;
                if (prune) {
                    contents = Pruner.prune(file.toUri(), contents, "isWord");
                }
                files.add(new StringFileObject(contents, file.toUri()));
            }
            return files;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JavaCompilerService createCompiler() {
        return new JavaCompilerService(
                Collections.singleton(sourceRoot),
                () -> {
                    throw new RuntimeException("Unimplemented");
                },
                Collections.emptySet(),
                Collections.emptySet());
    }

    @Benchmark
    public void pruned() {
        var compiler = createCompiler();
        compiler.compileBatch(pruned);
    }

    @Benchmark
    public void plain() {
        var compiler = createCompiler();
        compiler.compileBatch(files);
    }
}
