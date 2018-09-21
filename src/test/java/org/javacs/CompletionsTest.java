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
        var suggestions = insertText(file, 5, 38);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "class"));
        assertThat(suggestions, not(hasItems("testField", "testMethod", "getClass")));
    }

    @Test
    public void staticReference() throws IOException {
        var file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static methods
        var suggestions = insertText(file, 7, 48);

        assertThat(suggestions, hasItems("testMethod", "testMethodStatic", "new"));
        assertThat(suggestions, not(hasItems("class")));
    }

    @Test
    public void member() throws IOException {
        var file = "/org/javacs/example/AutocompleteMember.java";

        // Virtual testMethods
        var suggestions = insertText(file, 5, 14);

        assertThat(
                "excludes static members",
                suggestions,
                not(
                        hasItems(
                                "testFieldStatic",
                                "testMethodStatic",
                                "testFieldStaticPrivate",
                                "testMethodStaticPrivate",
                                "class",
                                "AutocompleteMember")));
        assertThat(
                "includes non-static members",
                suggestions,
                hasItems("testFields", "testMethods", "testFieldsPrivate", "testMethodsPrivate", "getClass"));
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

        assertThat(suggestions, hasItems("testMethods"));
        assertThat(details, hasItems("String () throws Exception"));
    }

    @Test
    public void fieldFromInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 8, 10);

        assertThat(suggestions, hasItems("testFields", "testFieldStatic", "testMethods", "testMethodStatic"));
    }

    @Test
    public void thisDotFieldFromInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        var suggestions = insertText(file, 9, 15);

        assertThat(suggestions, hasItems("testFields", "testMethods"));
        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic")));
    }

    @Test
    public void classDotFieldFromInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 10, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void fieldFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 22, 10);

        assertThat(
                suggestions,
                hasItems("testFields", "testFieldStatic", "testMethods", "testMethodStatic", "testArguments"));
    }

    @Test
    public void thisDotFieldFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        var suggestions = insertText(file, 23, 15);

        assertThat(suggestions, hasItems("testFields", "testMethods"));
        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic", "testArguments")));
    }

    @Test
    public void classDotFieldFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 24, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods", "testArguments")));
    }

    @Test
    public void thisRefMethodFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // this::m
        var suggestions = insertText(file, 25, 59);

        assertThat(suggestions, hasItems("testMethods"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic", "testMethodStatic")));
    }

    @Test
    public void classRefMethodFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        var suggestions = insertText(file, 26, 74);

        assertThat(suggestions, hasItems("testMethodStatic", "testMethods"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic")));
    }

    @Test
    @Ignore // javac doesn't give us helpful info about the fact that static initializers are static
    public void fieldFromStaticInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 16, 10);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void classDotFieldFromStaticInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void classRefFieldFromStaticInitBlock() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        var suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic", "testMethods")));
    }

    @Test
    public void fieldFromStaticMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        var suggestions = insertText(file, 30, 10);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "testArguments"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void classDotFieldFromStaticMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        var suggestions = insertText(file, 31, 30);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testMethods", "testArguments")));
    }

    @Test
    public void classRefFieldFromStaticMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteMembers.java";

        // TODO
        // AutocompleteMembers::m
        var suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("testMethodStatic"));
        assertThat(suggestions, not(hasItems("testFields", "testFieldStatic", "testMethods")));
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

        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic", "class")));
        assertThat(suggestions, not(hasItems("testFieldStaticPrivate", "testMethodStaticPrivate")));
        assertThat(suggestions, not(hasItems("testFieldsPrivate", "testMethodsPrivate")));
        assertThat(suggestions, hasItems("testFields", "testMethods", "getClass"));
    }

    @Test
    public void otherStatic() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.
        var suggestions = insertText(file, 7, 28);

        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "class"));
        assertThat(suggestions, not(hasItems("testFieldStaticPrivate", "testMethodStaticPrivate")));
        assertThat(suggestions, not(hasItems("testFieldsPrivate", "testMethodsPrivate")));
        assertThat(suggestions, not(hasItems("testFields", "testMethods", "getClass")));
    }

    @Test
    public void otherDotClassDot() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.class.
        var suggestions = insertText(file, 8, 33);

        assertThat(suggestions, hasItems("getName", "getClass"));
        assertThat(suggestions, not(hasItems("testFieldStatic", "testMethodStatic", "class")));
        assertThat(suggestions, not(hasItems("testFieldStaticPrivate", "testMethodStaticPrivate")));
        assertThat(suggestions, not(hasItems("testFieldsPrivate", "testMethodsPrivate")));
        assertThat(suggestions, not(hasItems("testFields", "testMethods")));
    }

    @Test
    public void otherClass() throws IOException {
        var file = "/org/javacs/example/AutocompleteOther.java";

        // Auto?
        var suggestions = insertText(file, 6, 13);

        assertThat(suggestions, hasItems("AutocompleteOther", "AutocompleteMember"));
    }

    @Test
    public void arrayLength() throws IOException {
        var file = "/org/javacs/example/AutocompleteArray.java";

        // a.?
        var suggestions = insertText(file, 7, 11);

        assertThat(suggestions, hasItems("length"));
    }

    @Ignore // We are now managing imports with FixImports
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

    @Ignore // We are now managing imports with FixImports
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

    @Ignore // We are now managing imports with FixImports
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

    @Ignore // We are now managing imports with FixImports
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

    @Ignore // We are now managing imports with FixImports
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

    @Ignore // We are now managing imports with FixImports
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

        assertThat(suggestions, not(hasItems("testMethodStatic")));
        assertThat(suggestions, hasItems("testMethods", "getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void docstring() throws IOException {
        var file = "/org/javacs/example/AutocompleteDocstring.java";
        var docstrings = documentation(file, 8, 14);

        assertThat(docstrings, hasItems("A testMethods", "A testFields"));

        docstrings = documentation(file, 12, 31);

        assertThat(docstrings, hasItems("A testFieldStatic", "A testMethodStatic"));
    }

    @Test
    public void classes() throws IOException {
        var file = "/org/javacs/example/AutocompleteClasses.java";

        // Fix?
        var suggestions = insertText(file, 5, 12);

        assertThat(suggestions, hasItems("FixParseErrorAfter"));

        // Some?
        suggestions = insertText(file, 6, 13);

        assertThat(suggestions, hasItems("SomeInnerClass"));
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

    @Ignore // We are now managing imports with FixImports
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

    @Ignore
    @Test
    public void importFromSource() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = insertText(file, 3, 12);

        assertThat("Does not have own package class", suggestions, hasItems("javacs"));
    }

    @Test
    public void importFromClasspath() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = insertText(file, 5, 13);

        assertThat("Has class from classpath", suggestions, hasItems("util"));
    }

    // TODO top level of import
    @Ignore
    @Test
    public void importFirstId() throws IOException {
        var file = "/org/javacs/example/AutocompletePackage.java";

        // import ?
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

        // Imports are now being managed by FixImports
        // for (var item : items) {
        //     if (item.getLabel().equals("OtherPackagePublic"))
        //         assertThat(
        //                 "Don't import when completing imports",
        //                 item.getAdditionalTextEdits(),
        //                 either(empty()).or(nullValue()));
        // }
    }

    @Test
    public void otherPackageId() throws IOException {
        var file = "/org/javacs/example/AutocompleteOtherPackage.java";

        // Static methods
        var items = items(file, 5, 14);
        var suggestions = Lists.transform(items, i -> i.getLabel());

        assertThat(suggestions, hasItems("OtherPackagePublic"));
        assertThat(suggestions, not(hasItems("OtherPackagePrivate")));

        // for (var item : items) {
        //     if (item.getLabel().equals("OtherPackagePublic"))
        //         assertThat("Auto-import OtherPackagePublic", item.getAdditionalTextEdits(), not(empty()));
        // }
    }

    @Test
    public void fieldFromStaticInner() throws IOException {
        var file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of static inner class
        var suggestions = insertText(file, 12, 14);

        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        // TODO this is not visible
        // assertThat(suggestions, not(hasItems("testMethods", "testFields")));
    }

    @Test
    public void fieldFromInner() throws IOException {
        var file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of inner class
        var suggestions = insertText(file, 18, 14);

        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        assertThat(suggestions, hasItems("testMethods", "testFields"));
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
        // TODO you can't actually make an inner enum
        // assertThat("does not suggest enum", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void newInnerClassFromMethod() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";

        // new Inner?
        var suggestions = insertText(file, 11, 18);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        // TODO you can't actually make an inner enum
        // assertThat("does not suggest enum", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void innerEnum() throws IOException {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = insertText(file, 15, 40);

        assertThat("suggests enum constants", suggestions, hasItems("Foo"));
    }

    @Test
    public void switchCase() throws IOException {
        var file = "/org/javacs/example/AutocompleteCase.java";
        var suggestions = insertText(file, 8, 18);

        assertThat("suggests enum options", suggestions, containsInAnyOrder("Foo", "Bar"));
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
        var suggestions = insertText(file, 11, 10);

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
    @Ignore
    public void onlySuggestOnce() throws IOException {
        var file = "/org/javacs/example/AutocompleteOnce.java";
        var suggestions = insertCount(file, 5, 18);

        assertThat("suggests Signatures", suggestions, hasKey("Signatures"));
        assertThat("suggests Signatures only once", suggestions, hasEntry("Signatures", 1));
    }

    @Test
    public void overloadedOnSourcePath() throws IOException {
        var file = "/org/javacs/example/OverloadedMethod.java";
        var detail = detail(file, 9, 13);

        assertThat("suggests empty method", detail, hasItem("overloaded()"));
        assertThat("suggests int method", detail, hasItem("overloaded(i)"));
        assertThat("suggests string method", detail, hasItem("overloaded(s)"));
    }

    @Test
    public void overloadedOnClassPath() throws IOException {
        var file = "/org/javacs/example/OverloadedMethod.java";
        var detail = detail(file, 10, 26);

        assertThat("suggests empty method", detail, hasItem("of()"));
        assertThat("suggests one-arg method", detail, hasItem("of(e1)"));
        // assertThat("suggests vararg method", detail, hasItem("of(elements)"));
    }
}
