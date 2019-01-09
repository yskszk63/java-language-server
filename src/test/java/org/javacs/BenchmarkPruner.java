package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
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
    private static List<SourceFileObject> files = files(false);
    private static List<SourceFileObject> pruned = files(true);

    private static List<SourceFileObject> files(boolean prune) {
        try {
            var files = new ArrayList<SourceFileObject>();
            var dir = Paths.get("src/main/java/org/javacs").normalize();
            var it = Files.list(dir).iterator();
            while (it.hasNext()) {
                var file = it.next();
                if (!Files.isRegularFile(file)) continue;
                if (prune) {
                    var contents = Pruner.prune(file.toUri(), "isWord");
                    files.add(new SourceFileObject(file, contents));
                } else {
                    files.add(new SourceFileObject(file));
                }
            }
            return files;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JavaCompilerService createCompiler() {
        return new JavaCompilerService(Collections.emptySet(), Collections.emptySet());
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
