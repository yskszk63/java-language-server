package org.javacs;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BenchmarkPruner {
    private static SourceFileObject file = file(false);
    private static SourceFileObject pruned = file(true);
    private static JavaCompilerService compiler = createCompiler();

    private static SourceFileObject file(boolean prune) {
        var file = Paths.get("src/main/java/org/javacs/JavaCompilerService.java").normalize();
        if (prune) {
            var contents = Pruner.prune(file.toUri(), "isWord");
            return new SourceFileObject(file, contents);
        } else {
            return new SourceFileObject(file);
        }
    }

    private static JavaCompilerService createCompiler() {
        var workspaceRoot = Paths.get(".").normalize().toAbsolutePath();
        FileStore.setWorkspaceRoots(Set.of(workspaceRoot));
        var classPath = new InferConfig(workspaceRoot).classPath();
        return new JavaCompilerService(classPath, Collections.emptySet());
    }

    @Benchmark
    public void pruned() {
        compiler.compileBatch(List.of(pruned));
    }

    @Benchmark
    public void plain() {
        compiler.compileBatch(List.of(file));
    }
}
