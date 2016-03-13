package com.fivetran.javac;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Type;
import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class LinterTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("");

    @Test
    public void compile() throws IOException {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();

        JavacTaskBuilder.create()
                        .addFile(new GetResourceFileObject("/HelloWorld.java"))
                        .reportErrors(errors)
                        .build()
                        .call();
    }

    @Test
    public void inspectTree() throws IOException {
        CollectMethods scanner = new CollectMethods();
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(new GetResourceFileObject("/HelloWorld.java"))
                                         .reportErrors(errors)
                                         .afterAnalyze(scanner)
                                         .build();

        task.call();

        assertThat(scanner.methodNames, hasItem("main"));
    }

    @Test
    public void missingMethodBody() throws IOException {
        CollectMethods scanner = new CollectMethods();
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(new GetResourceFileObject("/MissingMethodBody.java"))
                                         .reportErrors(errors)
                                         .afterAnalyze(scanner)
                                         .build();

        task.call();

        assertThat(scanner.methodNames, hasItem("test"));
        assertThat(errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void incompleteAssignment() throws IOException {
        CollectMethods parsed = new CollectMethods();
        CollectMethods compiled = new CollectMethods();
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(new GetResourceFileObject("/IncompleteAssignment.java"))
                                         .reportErrors(errors)
                                         .afterParse(parsed)
                                         .afterAnalyze(compiled)
                                         .build();

        task.call();

        assertThat(parsed.methodNames, hasItem("test")); // Error recovery should have worked
        assertThat(compiled.methodNames, hasItem("test")); // Type error recovery should have worked
        assertThat(errors.getDiagnostics(), not(empty()));
    }

    @Test
    public void undefinedSymbol() throws IOException {
        CollectMethods scanner = new CollectMethods();
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(new GetResourceFileObject("/UndefinedSymbol.java"))
                                         .afterAnalyze(scanner)
                                         .reportErrors(errors)
                                         .build();

        task.call();

        assertThat(scanner.methodNames, hasItem("test")); // Type error, so parse tree is present
        assertThat(errors.getDiagnostics(), not(empty()));

        Diagnostic<? extends JavaFileObject> d = errors.getDiagnostics().get(0);

        // Error position should span entire 'foo' symbol
        assertThat(d.getLineNumber(), greaterThan(0L));
        assertThat(d.getStartPosition(), greaterThan(0L));
        assertThat(d.getEndPosition(), greaterThan(d.getStartPosition() + 1));
    }

    @Test
    public void getType() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        MethodTypes scanner = new MethodTypes();
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(new GetResourceFileObject("/FooString.java"))
                                         .afterAnalyze(scanner)
                                         .reportErrors(errors)
                                         .build();

        task.call();

        assertThat(scanner.methodTypes, hasKey("test"));

        Type.MethodType type = scanner.methodTypes.get("test");

        assertThat(type.getReturnType().toString(), equalTo("java.lang.String"));
        assertThat(type.getParameterTypes(), hasSize(1));
        assertThat(type.getParameterTypes().get(0).toString(), equalTo("java.lang.String"));
    }

    @Test
    public void notJava() {
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        JavacTask task = JavacTaskBuilder.create()
                                         .addFile(new GetResourceFileObject("/NotJava.java.txt"))
                                         .reportErrors(errors)
                                         .build();

        task.call();

        assertThat(errors.getDiagnostics(), not(empty()));
    }

    public static class MethodTypes extends BridgeExpressionScanner {
        public final Map<String, Type.MethodType> methodTypes = new HashMap<>();

        @Override
        protected void visitMethod(MethodTree node) {
            super.visitMethod(node);

            Trees trees = Trees.instance(super.task);
            Type.MethodType typeMirror = (Type.MethodType) trees.getTypeMirror(path());

            methodTypes.put(node.getName().toString(), typeMirror);
        }
    }

    public static class CollectMethods extends BridgeExpressionScanner {
        public final Set<String> methodNames = new HashSet<>();

        @Override
        protected void visitMethod(MethodTree node) {
            super.visitMethod(node);

            methodNames.add(node.getName().toString());
        }
    }
}
