package org.javacs;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import org.openjdk.jmh.annotations.*;

// TODO this is coloring badly
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ErrorProneBenchmark {
    private static Path file = Paths.get(FindResource.uri("/org/javacs/example/BenchmarkStringSearch.java"));

    @State(Scope.Thread)
    public static class BenchmarkState {
        JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
        StandardJavaFileManager fileManager =
                new FileManagerWrapper(compiler.getStandardFileManager(__ -> {}, null, Charset.defaultCharset()));
    }

    @Benchmark
    public void vanilla(BenchmarkState state) {
        var options = JavaCompilerService.options(Set.of(), Set.of());

        analyze(state, options);
    }

    @Benchmark
    public void withErrorProne(BenchmarkState state) {
        var options = JavaCompilerService.options(Set.of(), Set.of());
        // Add error-prone
        options.addAll(errorProneOptions());

        analyze(state, options);
    }

    private void analyze(BenchmarkState state, List<String> options) {
        var sources = state.fileManager.getJavaFileObjectsFromPaths(List.of(file));
        // Create task
        var task =
                (JavacTask)
                        state.compiler.getTask(
                                null, state.fileManager, __ -> {}, options, Collections.emptyList(), sources);
        // Print timing information for optimization
        var profiler = new Profiler();
        try {
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> errorProneOptions() {
        var options = new ArrayList<String>();

        // https://errorprone.info/docs/installation "Command Line"
        Collections.addAll(options, "-XDcompilePolicy=byfile");
        Collections.addAll(options, "-processorpath", Lib.ERROR_PRONE);

        // https://errorprone.info/bugpatterns
        var bugPatterns = new StringJoiner(" ");
        // bugPatterns.add("-Xep:EmptyIf");
        // bugPatterns.add("-Xep:NumericEquality");
        // bugPatterns.add("-Xep:ConstructorLeaksThis");
        // bugPatterns.add("-Xep:EqualsBrokenForNull");
        // bugPatterns.add("-Xep:InvalidThrows");
        // bugPatterns.add("-Xep:RedundantThrows");
        // bugPatterns.add("-Xep:StaticQualifiedUsingExpression");
        // bugPatterns.add("-Xep:StringEquality");
        // bugPatterns.add("-Xep:Unused");
        // bugPatterns.add("-Xep:UnusedException");
        // bugPatterns.add("-Xep:FieldCanBeFinal");
        // bugPatterns.add("-Xep:FieldMissingNullable");
        // bugPatterns.add("-Xep:MethodCanBeStatic");
        // bugPatterns.add("-Xep:PackageLocation");
        // bugPatterns.add("-Xep:PrivateConstructorForUtilityClass");
        // bugPatterns.add("-Xep:ReturnMissingNullable");

        Collections.addAll(
                options, "-Xplugin:ErrorProne -XepAllErrorsAsWarnings " + bugPatterns + " --illegal-access=warn");

        return options;
    }
}
