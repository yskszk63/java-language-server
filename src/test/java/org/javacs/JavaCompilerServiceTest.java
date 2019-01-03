package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;

public class JavaCompilerServiceTest {
    private static final Logger LOG = Logger.getLogger("main");

    static {
        Main.setRootFormat();
    }

    private JavaCompilerService compiler =
            new JavaCompilerService(
                    Collections.singleton(simpleProjectSrc()),
                    JavaCompilerServiceTest::allJavaFiles,
                    Collections.emptySet(),
                    Collections.emptySet());

    static Path mavenProjectSrc() {
        return Paths.get("src/test/test-project/workspace/src").normalize();
    }

    static Path simpleProjectSrc() {
        return Paths.get("src/test/test-project/simple").normalize();
    }

    static Set<Path> allJavaFiles() {
        try {
            return Files.walk(simpleProjectSrc())
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String contents(String resourceFile) {
        var root = JavaCompilerServiceTest.simpleProjectSrc();
        var file = root.resolve(resourceFile);
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var join = new StringJoiner("\n");
        for (var l : lines) join.add(l);
        return join.toString();
    }

    private URI resourceUri(String resourceFile) {
        var root = JavaCompilerServiceTest.simpleProjectSrc();
        var file = root.resolve(resourceFile);
        return file.toUri();
    }

    @Test
    public void element() {
        var uri = resourceUri("HelloWorld.java");
        var contents = contents("HelloWorld.java");
        var found = compiler.compileFocus(uri, contents, 3, 24).element();

        assertThat(found.getSimpleName(), hasToString(containsString("println")));
    }

    @Test
    public void elementWithError() {
        var uri = resourceUri("CompleteMembers.java");
        var contents = contents("CompleteMembers.java");
        var found = compiler.compileFocus(uri, contents, 3, 12).element();

        assertThat(found, notNullValue());
    }

    private List<String> completionNames(List<Completion> found) {
        var result = new ArrayList<String>();
        for (var c : found) {
            if (c.element != null) result.add(c.element.getSimpleName().toString());
            else if (c.packagePart != null) result.add(c.packagePart.name);
            else if (c.keyword != null) result.add(c.keyword);
            else if (c.className != null) result.add(Parser.lastName(c.className.name));
            else if (c.snippet != null) result.add(c.snippet.snippet);
        }
        return result;
    }

    private List<String> elementNames(List<Element> found) {
        return found.stream().map(e -> e.getSimpleName().toString()).collect(Collectors.toList());
    }

    @Test
    public void identifiers() {
        var uri = resourceUri("CompleteIdentifiers.java");
        var contents = contents("CompleteIdentifiers.java");
        var focus = compiler.compileFocus(uri, contents, 13, 21);
        var found = focus.scopeMembers("complete");
        var names = elementNames(found);
        assertThat(names, hasItem("completeLocal"));
        assertThat(names, hasItem("completeParam"));
        //        assertThat(names, hasItem("super"));
        //        assertThat(names, hasItem("this"));
        assertThat(names, hasItem("completeOtherMethod"));
        assertThat(names, hasItem("completeInnerField"));
        assertThat(names, hasItem("completeOuterField"));
        assertThat(names, hasItem("completeOuterStatic"));
        // assertThat(names, hasItem("CompleteIdentifiers"));
    }

    @Test
    public void identifiersInMiddle() {
        var uri = resourceUri("CompleteInMiddle.java");
        var contents = contents("CompleteInMiddle.java");
        var focus = compiler.compileFocus(uri, contents, 13, 21);
        var found = focus.scopeMembers("complete");
        var names = elementNames(found);
        assertThat(names, hasItem("completeLocal"));
        assertThat(names, hasItem("completeParam"));
        //        assertThat(names, hasItem("super"));
        //        assertThat(names, hasItem("this"));
        assertThat(names, hasItem("completeOtherMethod"));
        assertThat(names, hasItem("completeInnerField"));
        assertThat(names, hasItem("completeOuterField"));
        assertThat(names, hasItem("completeOuterStatic"));
        // assertThat(names, hasItem("CompleteInMiddle"));
    }

    @Test
    public void completeIdentifiers() {
        var uri = resourceUri("CompleteIdentifiers.java");
        var contents = contents("CompleteIdentifiers.java");
        var ctx = compiler.parseFile(uri, contents).completionContext(13, 21).get();
        var focus = compiler.compileFocus(uri, contents, ctx.line, ctx.character);
        var found = focus.completeIdentifiers(ctx.inClass, ctx.inMethod, ctx.partialName);
        var names = completionNames(found);
        assertThat(names, hasItem("completeLocal"));
        assertThat(names, hasItem("completeParam"));
        //        assertThat(names, hasItem("super"));
        //        assertThat(names, hasItem("this"));
        assertThat(names, hasItem("completeOtherMethod"));
        assertThat(names, hasItem("completeInnerField"));
        assertThat(names, hasItem("completeOuterField"));
        assertThat(names, hasItem("completeOuterStatic"));
        //        assertThat(names, hasItem("CompleteIdentifiers"));
    }

    @Test
    public void members() {
        var uri = resourceUri("CompleteMembers.java");
        var contents = contents("CompleteMembers.java");
        var focus = compiler.compileFocus(uri, contents, 3, 14);
        var found = focus.completeMembers(false);
        var names = completionNames(found);
        assertThat(names, hasItem("subMethod"));
        assertThat(names, hasItem("superMethod"));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeMembers() {
        var uri = resourceUri("CompleteMembers.java");
        var contents = contents("CompleteMembers.java");
        var ctx = compiler.parseFile(uri, contents).completionContext(3, 15).get();
        var focus = compiler.compileFocus(uri, contents, ctx.line, ctx.character);
        var found = focus.completeMembers(false);
        var names = completionNames(found);
        assertThat(names, hasItem("subMethod"));
        assertThat(names, hasItem("superMethod"));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeExpression() {
        var uri = resourceUri("CompleteExpression.java");
        var contents = contents("CompleteExpression.java");
        var ctx = compiler.parseFile(uri, contents).completionContext(3, 37).get();
        var focus = compiler.compileFocus(uri, contents, ctx.line, ctx.character);
        var found = focus.completeMembers(false);
        var names = completionNames(found);
        assertThat(names, hasItem("instanceMethod"));
        assertThat(names, not(hasItem("create")));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeClass() {
        var uri = resourceUri("CompleteClass.java");
        var contents = contents("CompleteClass.java");
        var ctx = compiler.parseFile(uri, contents).completionContext(3, 23).get();
        var focus = compiler.compileFocus(uri, contents, ctx.line, ctx.character);
        var found = focus.completeMembers(false);
        var names = completionNames(found);
        assertThat(names, hasItems("staticMethod", "staticField"));
        assertThat(names, hasItems("class"));
        assertThat(names, not(hasItem("instanceMethod")));
        assertThat(names, not(hasItem("instanceField")));
    }

    @Test
    public void completeImports() {
        var uri = resourceUri("CompleteImports.java");
        var contents = contents("CompleteImports.java");
        var ctx = compiler.parseFile(uri, contents).completionContext(1, 18).get();
        var focus = compiler.compileFocus(uri, contents, ctx.line, ctx.character);
        var found = focus.completeMembers(false);
        var names = completionNames(found);
        assertThat(names, hasItem("List"));
        assertThat(names, hasItem("concurrent"));
    }

    @Test
    public void overloads() {
        var uri = resourceUri("Overloads.java");
        var contents = contents("Overloads.java");
        var found = compiler.compileFocus(uri, contents, 3, 15).methodInvocation().get();
        var strings = found.overloads.stream().map(Object::toString).collect(Collectors.toList());

        assertThat(strings, hasItem(containsString("print(int)")));
        assertThat(strings, hasItem(containsString("print(java.lang.String)")));
    }

    @Test
    public void reportErrors() {
        var uri = resourceUri("HasError.java");
        var files = Collections.singleton(uri);
        var diags = compiler.reportErrors(files);
        assertThat(diags, not(empty()));
    }

    private static List<String> errorStrings(List<Diagnostic<? extends JavaFileObject>> diags) {
        var strings = new ArrayList<String>();
        for (var d : diags) {
            var file = Parser.fileName(d.getSource().toUri());
            var line = d.getLineNumber();
            var kind = d.getKind();
            var msg = d.getMessage(null);
            var string = String.format("%s(%d): %s", file, line, msg);
            strings.add(string);
        }
        return strings;
    }

    // TODO get these back somehow
    @Test
    @Ignore
    public void errorProne() {
        var uri = resourceUri("ErrorProne.java");
        var files = Collections.singleton(uri);
        var diags = compiler.reportErrors(files);
        var strings = errorStrings(diags);
        assertThat(strings, hasItem(containsString("ErrorProne.java(7): [CollectionIncompatibleType]")));
    }

    // TODO get these back somehow
    @Test
    @Ignore
    public void unusedVar() {
        var uri = resourceUri("UnusedVar.java");
        var files = Collections.singleton(uri);
        var diags = compiler.reportErrors(files);
        var strings = errorStrings(diags);
        assertThat(strings, hasItem(containsString("UnusedVar.java(3): [Unused]")));
    }

    @Test
    public void localDoc() {
        var uri = resourceUri("LocalMethodDoc.java");
        var contents = contents("LocalMethodDoc.java");
        var method = compiler.compileFocus(uri, contents, 3, 21).methodInvocation().get().activeMethod.get();
        var ptr = new Ptr(method);
        var file = compiler.docs().find(ptr).get();
        var parse = compiler.docs().parse(file);
        var path = parse.fuzzyFind(ptr).get();
        var doc = parse.doc(path);
        assertThat(doc.toString(), containsString("A great method"));
    }

    @Test
    public void fixImports() {
        var uri = resourceUri("MissingImport.java");
        var contents = contents("MissingImport.java");
        var qualifiedNames = compiler.compileFile(uri, contents).fixImports();
        assertThat(qualifiedNames, hasItem("java.util.List"));
    }

    @Test
    public void matchesPartialName() {
        assertTrue(CompileFocus.matchesPartialName("foobar", "foo"));
        assertFalse(CompileFocus.matchesPartialName("foo", "foobar"));
    }

    @Test
    public void packageName() {
        var compiler =
                new JavaCompilerService(
                        Collections.singleton(mavenProjectSrc()),
                        JavaCompilerServiceTest::allJavaFiles,
                        Collections.emptySet(),
                        Collections.emptySet());
        assertThat(
                compiler.pathBasedPackageName(FindResource.path("/org/javacs/example/Goto.java")),
                equalTo("org.javacs.example"));
    }
}
