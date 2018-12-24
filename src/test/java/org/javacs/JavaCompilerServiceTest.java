package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.Test;

public class JavaCompilerServiceTest {
    private static final Logger LOG = Logger.getLogger("main");

    private JavaCompilerService compiler =
            new JavaCompilerService(
                    Collections.singleton(resourcesDir()), Collections.emptySet(), Collections.emptySet());

    static Path resourcesDir() {
        try {
            return Paths.get(JavaCompilerServiceTest.class.getResource("/HelloWorld.java").toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String contents(String resourceFile) {
        try (var in = JavaCompilerServiceTest.class.getResourceAsStream(resourceFile)) {
            return new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private URI resourceUri(String resourceFile) {
        try {
            return JavaCompilerServiceTest.class.getResource(resourceFile).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void element() {
        var found = compiler.element(resourceUri("/HelloWorld.java"), contents("/HelloWorld.java"), 3, 24);

        assertThat(found.getSimpleName(), hasToString(containsString("println")));
    }

    @Test
    public void elementWithError() {
        var found = compiler.element(resourceUri("/CompleteMembers.java"), contents("/CompleteMembers.java"), 3, 12);

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
        var found =
                compiler.scopeMembers(
                        resourceUri("/CompleteIdentifiers.java"),
                        contents("/CompleteIdentifiers.java"),
                        13,
                        21,
                        "complete");
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
        var found =
                compiler.scopeMembers(
                        resourceUri("/CompleteInMiddle.java"), contents("/CompleteInMiddle.java"), 13, 21, "complete");
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
        var found =
                compiler.completions(
                                resourceUri("/CompleteIdentifiers.java"), contents("/CompleteIdentifiers.java"), 13, 21)
                        .items;
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
        var found =
                compiler.members(resourceUri("/CompleteMembers.java"), contents("/CompleteMembers.java"), 3, 14, false);
        var names = completionNames(found);
        assertThat(names, hasItem("subMethod"));
        assertThat(names, hasItem("superMethod"));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeMembers() {
        var found =
                compiler.completions(resourceUri("/CompleteMembers.java"), contents("/CompleteMembers.java"), 3, 15)
                        .items;
        var names = completionNames(found);
        assertThat(names, hasItem("subMethod"));
        assertThat(names, hasItem("superMethod"));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeExpression() {
        var found =
                compiler.completions(
                                resourceUri("/CompleteExpression.java"), contents("/CompleteExpression.java"), 3, 37)
                        .items;
        var names = completionNames(found);
        assertThat(names, hasItem("instanceMethod"));
        assertThat(names, not(hasItem("create")));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeClass() {
        var found =
                compiler.completions(resourceUri("/CompleteClass.java"), contents("/CompleteClass.java"), 3, 23).items;
        var names = completionNames(found);
        assertThat(names, hasItems("staticMethod", "staticField"));
        assertThat(names, hasItems("class"));
        assertThat(names, not(hasItem("instanceMethod")));
        assertThat(names, not(hasItem("instanceField")));
    }

    @Test
    public void completeImports() {
        var found =
                compiler.completions(resourceUri("/CompleteImports.java"), contents("/CompleteImports.java"), 1, 18)
                        .items;
        var names = completionNames(found);
        assertThat(names, hasItem("List"));
        assertThat(names, hasItem("concurrent"));
    }

    /*
    @Test
    public void gotoDefinition() {
        var def =
                compiler.definition("/GotoDefinition.java";
        assertTrue(def.isPresent());

        var t = def.get();
        var unit = t.getCompilationUnit();
        assertThat(unit.getSourceFile().getName(), endsWith("GotoDefinition.java"));

        var trees = compiler.trees();
        var pos = trees.getSourcePositions();
        var lines = unit.getLineMap();
        long start = pos.getStartPosition(unit, t.getLeaf());
        long line = lines.getLineNumber(start);
        assertThat(line, equalTo(6L));
    }
    */

    private final ReportReferencesProgress rrp =
            new ReportReferencesProgress() {
                @Override
                public void scanForPotentialReferences(int nScanned, int nFiles) {}

                @Override
                public void checkPotentialReferences(int nCompiled, int nPotential) {}
            };

    @Test
    public void references() {
        var file = "/GotoDefinition.java";
        var refs = compiler.references(resourceUri(file), contents(file), 6, 13, rrp);
        var stringify = new ArrayList<String>();
        for (var r : refs) {
            var fileName = Paths.get(r.fromFile).getFileName();
            stringify.add(String.format("%s:%d", fileName, r.startLine));
        }
        assertThat(stringify, hasItem("GotoDefinition.java:3"));
        assertThat(stringify, not(hasItem("GotoDefinition.java:6")));
    }

    @Test
    public void referencesFile() {
        var file = "/GotoDefinition.java";
        var refs = compiler.referencesFile(resourceUri(file), contents(file), rrp);
        var stringify = new ArrayList<String>();
        for (var r : refs) {
            var fileName = Paths.get(r.fromFile).getFileName();
            stringify.add(String.format("%s:%d", fileName, r.startLine));
        }
        assertThat(stringify, hasItem("GotoDefinition.java:3"));
    }

    @Test
    public void overloads() {
        var found = compiler.methodInvocation(resourceUri("/Overloads.java"), contents("/Overloads.java"), 3, 15).get();
        var strings = found.overloads.stream().map(Object::toString).collect(Collectors.toList());

        assertThat(strings, hasItem(containsString("print(int)")));
        assertThat(strings, hasItem(containsString("print(java.lang.String)")));
    }

    @Test
    public void lint() {
        List<Diagnostic<? extends JavaFileObject>> diags =
                compiler.lint(Collections.singleton(Paths.get(resourceUri("/HasError.java"))));
        assertThat(diags, not(empty()));
    }

    @Test
    public void localDoc() {
        var method =
                compiler.methodInvocation(resourceUri("/LocalMethodDoc.java"), contents("/LocalMethodDoc.java"), 3, 21)
                        .get()
                        .activeMethod
                        .get();
        var doc = compiler.methodDoc(method);
        assertTrue(doc.isPresent());
        assertThat(doc.toString(), containsString("A great method"));
    }

    @Test
    public void fixImports() {
        var qualifiedNames =
                compiler.fixImports(resourceUri("/MissingImport.java"), contents("/MissingImport.java")).fixedImports;
        assertThat(qualifiedNames, hasItem("java.util.List"));
    }

    @Test
    public void matchesPartialName() {
        assertTrue(JavaCompilerService.matchesPartialName("foobar", "foo"));
        assertFalse(JavaCompilerService.matchesPartialName("foo", "foobar"));
    }
}
