package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CompletionItem;
import org.junit.Ignore;
import org.junit.Test;

public class CompletionsTest extends CompletionsBase {

    @Test
    public void staticMember() throws IOException {
        var file = "/org/javacs/example/AutocompleteStaticMember.java";

        // Static methods
        var suggestions = insertText(file, 5, 34);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("fields", "methods", "getClass")));
    }

    @Test
    public void staticReference() throws IOException {
        var file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static methods
        var suggestions = insertText(file, 7, 44);

        assertThat(suggestions, hasItems("methods", "methodStatic"));
        assertThat(suggestions, not(hasItems("new")));
    }

    @Test
    public void member() throws IOException {
        var file = "/org/javacs/example/AutocompleteMember.java";

        // Virtual methods
        var suggestions = insertText(file, 5, 14);

        assertThat(
                "excludes static members",
                suggestions,
                not(
                        hasItems(
                                "fieldStatic",
                                "methodStatic",
                                "fieldStaticPrivate",
                                "methodStaticPrivate",
                                "class",
                                "AutocompleteMember")));
        assertThat(
                "includes non-static members",
                suggestions,
                hasItems("fields", "methods", "fieldsPrivate", "methodsPrivate", "getClass"));
        assertThat("excludes constructors", suggestions, not(hasItem(startsWith("AutocompleteMember"))));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void throwsSignature() throws IOException {
        var file = "/org/javacs/example/AutocompleteMember.java";

        // Static methods
        var items = items(file, 5, 14);
        var suggestions = items.stream().map(i -> i.getLabel()).collect(Collectors.toSet());
        var details = items.stream().map(i -> i.getDetail()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("methods"));
        assertThat(details, hasItems("String () throws Exception"));
    }

    @Test
    public void fieldFromInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 8, 10);

        assertThat(suggestions, hasItems("fields", "fieldStatic", "methods", "methodStatic"));
    }

    @Test
    public void thisDotFieldFromInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        var suggestions = insertText(file, 9, 15);

        assertThat(suggestions, hasItems("fields", "methods"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic")));
    }

    @Test
    public void classDotFieldFromInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 10, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "methods")));
    }

    @Test
    public void fieldFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 22, 10);

        assertThat(suggestions, hasItems("fields", "fieldStatic", "methods", "methodStatic", "arguments"));
    }

    @Test
    public void thisDotFieldFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        var suggestions = insertText(file, 23, 15);

        assertThat(suggestions, hasItems("fields", "methods"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "arguments")));
    }

    @Test
    public void classDotFieldFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 24, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "methods", "arguments")));
    }

    @Test
    public void thisRefMethodFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this::m
        var suggestions = insertText(file, 25, 59);

        assertThat(suggestions, hasItems("methods"));
        assertThat(suggestions, not(hasItems("fields", "fieldStatic", "methodStatic")));
    }

    @Test
    public void classRefMethodFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        var suggestions = insertText(file, 26, 74);

        assertThat(suggestions, hasItems("methodStatic", "methods"));
        assertThat(suggestions, not(hasItems("fields", "fieldStatic")));
    }

    @Test
    @Ignore // javac doesn't give us helpful info about the fact that static initializers are static
    public void fieldFromStaticInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 16, 10);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "methods")));
    }

    @Test
    public void classDotFieldFromStaticInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "methods")));
    }

    @Test
    public void classRefFieldFromStaticInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        var suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "fieldStatic", "methods")));
    }

    @Test
    public void fieldFromStaticMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 30, 10);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "arguments"));
        assertThat(suggestions, not(hasItems("fields", "methods")));
    }

    @Test
    public void classDotFieldFromStaticMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 31, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "methods", "arguments")));
    }

    @Test
    public void classRefFieldFromStaticMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // TODO
        // AutocompleteMembers::m
        var suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems("fields", "fieldStatic", "methods")));
    }

    private static String sortText(CompletionItem i) {
        if (i.getSortText() != null) return i.getSortText();
        else return i.getLabel();
    }

    @Test
    public void otherMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // new AutocompleteMember().
        var suggestions = insertText(file, 5, 34);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldsPrivate", "methodsPrivate")));
        assertThat(suggestions, hasItems("fields", "methods", "getClass"));
    }

    @Test
    public void otherStatic() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.
        var suggestions = insertText(file, 7, 28);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldsPrivate", "methodsPrivate")));
        assertThat(suggestions, not(hasItems("fields", "methods", "getClass")));
    }

    @Test
    public void otherDotClassDot() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.class.
        var suggestions = insertText(file, 8, 33);

        assertThat(suggestions, hasItems("getName", "getClass"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldsPrivate", "methodsPrivate")));
        assertThat(suggestions, not(hasItems("fields", "methods")));
    }

    @Test
    public void otherClass() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var suggestions = insertText(file, 6, 10);

        // String is in root scope, List is in import java.util.*
        assertThat(suggestions, hasItems("AutocompleteOther", "AutocompleteMember"));
    }

    @Test
    public void addImport() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 9, 17);

        for (var item : items) {
            if ("ArrayList".equals(item.getLabel())) {
                assertThat(item.getAdditionalTextEdits(), not(nullValue()));
                assertThat(item.getAdditionalTextEdits(), not(empty()));

                return;
            }
        }

        fail("No ArrayList in " + items);
    }

    @Test
    public void dontImportSamePackage() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 6, 10);

        for (var item : items) {
            if ("AutocompleteMember".equals(item.getLabel())) {
                assertThat(item.getAdditionalTextEdits(), either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No AutocompleteMember in " + items);
    }

    @Test
    public void dontImportJavaLang() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 11, 38);

        for (var item : items) {
            if ("ArrayIndexOutOfBoundsException".equals(item.getLabel())) {
                assertThat(item.getAdditionalTextEdits(), either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No ArrayIndexOutOfBoundsException in " + items);
    }

    @Test
    public void dontImportSelf() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 6, 10);

        for (var item : items) {
            if ("AutocompleteOther".equals(item.getLabel())) {
                assertThat(item.getAdditionalTextEdits(), either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No AutocompleteOther in " + items);
    }

    @Test
    public void dontImportAlreadyImported() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 12, 14);

        for (var item : items) {
            if ("Arrays".equals(item.getLabel())) {
                assertThat(item.getAdditionalTextEdits(), either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No Arrays in " + items);
    }

    @Test
    public void dontImportAlreadyImportedStar() {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        var items = items(file, 10, 26);

        for (var item : items) {
            if ("ArrayBlockingQueue".equals(item.getLabel())) {
                assertThat(item.getAdditionalTextEdits(), either(empty()).or(nullValue()));

                return;
            }
        }

        fail("No ArrayBlockingQueue in " + items);
    }

    @Test
    public void fromClasspath() throws IOException {
        var file = "/org/javacs/example/AutocompleteFromClasspath.java";

        // Static methods
        var items = items(file, 8, 17);
        var suggestions = items.stream().map(i -> i.getLabel()).collect(Collectors.toSet());
        var details = items.stream().map(i -> i.getDetail()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("add", "addAll"));
    }

    @Test
    public void betweenLines() throws IOException {
        var file = "/org/javacs/example/AutocompleteBetweenLines.java";

        // Static methods
        var suggestions = insertText(file, 9, 18);

        assertThat(suggestions, hasItems("add"));
    }

    @Test
    public void reference() throws IOException {
        var file = "/org/javacs/example/AutocompleteReference.java";

        // Static methods
        var suggestions = insertTemplate(file, 7, 21);

        assertThat(suggestions, not(hasItems("methodStatic")));
        assertThat(suggestions, hasItems("methods", "getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void docstring() throws IOException {
        var file = "/org/javacs/example/AutocompleteDocstring.java";
        var docstrings = documentation(file, 8, 14);

        assertThat(docstrings, hasItems("A methods", "A fields"));

        docstrings = documentation(file, 12, 31);

        assertThat(docstrings, hasItems("A fieldStatic", "A methodStatic"));
    }

    @Test
    public void classes() throws IOException {
        var file = "/org/javacs/example/AutocompleteClasses.java";

        // Static methods
        var suggestions = insertText(file, 5, 10);

        assertThat(suggestions, hasItems("FixParseErrorAfter", "SomeInnerClass"));
    }

    @Test
    public void editMethodName() throws IOException {
        var file = "/org/javacs/example/AutocompleteEditMethodName.java";

        // Static methods
        var suggestions = insertText(file, 5, 21);

        assertThat(suggestions, hasItems("getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void restParams() throws IOException {
        var file = "/org/javacs/example/AutocompleteRest.java";

        // Static methods
        var items = items(file, 5, 18);
        var suggestions = items.stream().map(i -> i.getLabel()).collect(Collectors.toSet());
        var details = items.stream().map(i -> i.getDetail()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("restMethod"));
        assertThat(details, hasItems("void (String... params)"));
    }

    @Test
    public void constructor() throws IOException {
        var file = "/org/javacs/example/AutocompleteConstructor.java";

        // Static methods
        var suggestions = insertText(file, 5, 25);

        assertThat(suggestions, hasItem(startsWith("AutocompleteConstructor")));
        assertThat(suggestions, hasItem(startsWith("AutocompleteMember")));
    }

    @Test
    public void autoImportConstructor() throws IOException {
        var file = "/org/javacs/example/AutocompleteConstructor.java";

        // Static methods
        var items = items(file, 6, 19);
        var suggestions = Lists.transform(items, i -> i.getInsertText());

        assertThat(suggestions, hasItems("ArrayList<>($0)"));

        for (var each : items) {
            if (each.getInsertText().equals("ArrayList<>"))
                assertThat(
                        "new ? auto-imports", each.getAdditionalTextEdits(), both(not(empty())).and(not(nullValue())));
        }
    }

    @Test
    public void importFromSource() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var suggestions = insertText(file, 3, 12);

        assertThat("Does not have own package class", suggestions, hasItems("javacs"));
    }

    @Test
    public void importFromClasspath() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var suggestions = insertText(file, 5, 13);

        assertThat("Has class from classpath", suggestions, hasItems("util"));
    }

    @Test
    public void importFirstId() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var suggestions = insertText(file, 7, 9);

        assertThat("Has class from classpath", suggestions, hasItems("com", "org"));
    }

    @Test
    public void emptyClasspath() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var suggestions = insertText(file, 6, 12);

        assertThat("Has deeply nested class", suggestions, not(hasItems("google.common.collect.Lists")));
    }

    @Test
    public void importClass() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // Static methods
        var items = items(file, 4, 25);
        var suggestions = Lists.transform(items, i -> i.getLabel());

        assertThat(suggestions, hasItems("OtherPackagePublic"));
        assertThat(suggestions, not(hasItems("OtherPackagePrivate")));

        for (var item : items) {
            if (item.getLabel().equals("OtherPackagePublic"))
                assertThat(
                        "Don't import when completing imports",
                        item.getAdditionalTextEdits(),
                        either(empty()).or(nullValue()));
        }
    }

    @Test
    public void otherPackageId() throws IOException {
        var file = "/org/javacs/example/AutocompleteOtherPackage.java";

        // Static methods
        var items = items(file, 5, 14);
        var suggestions = Lists.transform(items, i -> i.getLabel());

        assertThat(suggestions, hasItems("OtherPackagePublic"));
        assertThat(suggestions, not(hasItems("OtherPackagePrivate")));

        for (var item : items) {
            if (item.getLabel().equals("OtherPackagePublic"))
                assertThat("Auto-import OtherPackagePublic", item.getAdditionalTextEdits(), not(empty()));
        }
    }

    @Test
    public void fieldFromStaticInner() throws IOException {
        var file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of static inner class
        var suggestions = insertText(file, 12, 14);

        assertThat(suggestions, hasItems("methodStatic", "fieldStatic"));
        assertThat(suggestions, not(hasItems("methods", "fields")));
    }

    @Test
    public void fieldFromInner() throws IOException {
        var file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of inner class
        var suggestions = insertText(file, 18, 14);

        assertThat(suggestions, hasItems("methodStatic", "fieldStatic"));
        assertThat(suggestions, hasItems("methods", "fields"));
    }

    @Test
    public void classDotClassFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // AutocompleteInners.I
        var suggestions = insertText(file, 5, 29);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerClassFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // I
        var suggestions = insertText(file, 6, 10);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void newClassDotInnerClassFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // new AutocompleteInners.I
        var suggestions = insertText(file, 10, 33);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void newInnerClassFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // new I
        var suggestions = insertText(file, 11, 14);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void innerEnum() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = insertText(file, 15, 40);

        assertThat("suggests enum constants", suggestions, hasItems("Foo"));
    }

    @Test
    public void staticStarImport() throws IOException {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = insertText(file, 9, 15);

        assertThat("suggests star-imported static method", suggestions, hasItems("emptyList"));
    }

    @Test
    public void staticImport() throws IOException {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = insertText(file, 10, 10);

        assertThat("suggests star-imported static field", suggestions, hasItems("BC"));
    }

    @Test
    public void staticImportSourcePath() throws IOException {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = insertText(file, 11, 20);

        assertThat(
                "suggests star-imported public static field from source path",
                suggestions,
                hasItems("publicStaticFinal"));
        assertThat(
                "suggests star-imported package-private static field from source path",
                suggestions,
                hasItems("packagePrivateStaticFinal"));
    }

    @Test
    public void withinConstructor() throws IOException {
        var file = "/org/javacs/example/AutocompleteContext.java";
        var suggestions = insertText(file, 8, 38);

        assertThat("suggests local variable", suggestions, hasItems("length"));
    }

    @Test
    public void onlySuggestOnce() throws IOException {
        var file = "/org/javacs/example/AutocompleteOnce.java";
        var suggestions = insertCount(file, 5, 18);

        assertThat("suggests Signatures", suggestions, hasKey("Signatures"));
        assertThat("suggests Signatures only once", suggestions, hasEntry("Signatures", 1));
    }
}
