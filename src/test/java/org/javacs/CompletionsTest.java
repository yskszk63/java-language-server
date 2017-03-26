package org.javacs;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class CompletionsTest extends CompletionsBase {

    @Test
    public void staticMember() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticMember.java";

        // Static method
        Set<String> suggestions = insertText(file, 5, 34);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    @Ignore
    public void staticReference() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static method
        Set<String> suggestions = insertText(file, 3, 38);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems( "method", "new")));
    }

    @Test
    public void member() throws IOException {
        String file = "/org/javacs/example/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = insertText(file, 5, 14);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "fieldStaticPrivate", "methodStaticPrivate", "class")));
        assertThat(suggestions, hasItems("field", "method", "fieldPrivate", "methodPrivate", "getClass"));
    }

    @Test
    public void throwsSignature() throws IOException {
        String file = "/org/javacs/example/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = items(file, 5, 14).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("method() throws Exception"));
    }

    @Test
    public void fieldFromInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 8, 10);

        assertThat(suggestions, hasItems("field", "fieldStatic", "method", "methodStatic"));
    }

    @Test
    public void thisDotFieldFromInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";
        
        // this.f
        Set<String> suggestions = insertText(file, 9, 15);

        assertThat(suggestions, hasItems("field", "method"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic")));
    }

    @Test
    public void classDotFieldFromInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";
        
        // AutocompleteMembers.f
        Set<String> suggestions = insertText(file, 10, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method")));

        // TODO
//        // this::m
//        suggestions = insertText(file, 10, 15);
//
//        assertThat(suggestions, hasItems("method"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "methodStatic")));
//
//        // AutocompleteMembers::m
//        suggestions = insertText(file, 11, 30);
//
//        assertThat(suggestions, hasItems("methodStatic"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }

    @Test
    public void fieldFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 22, 10);

        assertThat(suggestions, hasItems("field", "fieldStatic", "method", "methodStatic", "argument"));
    }

    @Test
    public void thisDotFieldFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // this.f
        Set<String> suggestions = insertText(file, 23, 15);

        assertThat(suggestions, hasItems("field", "method"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "argument")));
    }

    @Test
    public void classDotFieldFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";
        
        // AutocompleteMembers.f
        Set<String> suggestions = insertText(file, 24, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method", "argument")));
    }

    @Test
    public void thisRefMethodFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // this::m
        Set<String> suggestions = insertText(file, 25, 16);

        assertThat(suggestions, hasItems("method"));
        assertThat(suggestions, not(hasItems("field", "fieldStatic", "methodStatic")));
    }

    @Test
    public void classRefMethodFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        Set<String> suggestions = insertText(file, 26, 31);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }

    @Test
    public void fieldFromStaticInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 16, 10);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method")));
    }

    @Test
    public void classDotFieldFromStaticInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers.f
        Set<String> suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method")));
    }

    @Test
    public void classRefFieldFromStaticInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // AutocompleteMembers::m
        Set<String> suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }

    @Test
    public void fieldFromStaticMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 30, 10);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "argument"));
        assertThat(suggestions, not(hasItems("field", "method")));
    }

    @Test
    public void classDotFieldFromStaticMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";
        
        // AutocompleteMembers.f
        Set<String> suggestions = insertText(file, 31, 30);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method", "argument")));
    }

    @Test
    public void classRefFieldFromStaticMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // TODO
        // AutocompleteMembers::m
        Set<String> suggestions = insertText(file, 17, 30);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }
    
    @Test
    public void order() throws IOException {
        String file = "/org/javacs/example/AutocompleteOrder.java";

        // this.
        Set<String> suggestions = items(file, 4, 26).stream().map(i -> i.getSortText()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("0/getMethod()", "1/getInheritedMethod()", "2/getClass()"));
        
        // identifier
        suggestions = items(file, 6, 9).stream().map(i -> i.getSortText()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("0/localVariable", "0/parameter", "1/test(String parameter)", "2/AutocompleteOrder"));
    }

    @Test
    public void otherMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // new AutocompleteMember().
        Set<String> suggestions = insertText(file, 5, 34);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldPrivate", "methodPrivate")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    public void otherStatic() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.
        Set<String> suggestions = insertText(file, 7, 28);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldPrivate", "methodPrivate")));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    public void otherDotClassDot() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // AutocompleteMember.class.
        Set<String> suggestions = insertText(file, 8, 33);

        assertThat(suggestions, hasItems("getName", "getClass"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldPrivate", "methodPrivate")));
        assertThat(suggestions, not(hasItems("field", "method")));
    }

    @Test
    public void otherClass() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        Set<String> suggestions = insertText(file, 6, 21);

        // String is in root scope, List is in import java.util.*
        assertThat(suggestions, hasItems("AutocompleteOther", "AutocompleteMember", "String", "List"));
    }

    @Test
    public void fromClasspath() throws IOException {
        String file = "/org/javacs/example/AutocompleteFromClasspath.java";

        // Static method
        Set<String> suggestions = items(file, 8, 17).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("add(E)", "add(int, E)"));
    }

    @Test
    public void betweenLines() throws IOException {
        String file = "/org/javacs/example/AutocompleteBetweenLines.java";

        // Static method
        Set<String> suggestions = insertText(file, 9, 18);

        assertThat(suggestions, hasItems("add"));
    }

    @Test
    @Ignore
    public void reference() throws IOException {
        String file = "/org/javacs/example/AutocompleteReference.java";

        // Static method
        Set<String> suggestions = insertText(file, 3, 15);

        assertThat(suggestions, not(hasItems("methodStatic")));
        assertThat(suggestions, hasItems("method", "getClass"));
    }

    @Test
    public void docstring() throws IOException {
        String file = "/org/javacs/example/AutocompleteDocstring.java";

        Set<String> docstrings = documentation(file, 8, 14);

        assertThat(docstrings, hasItems("A method", "A field"));

        docstrings = documentation(file, 12, 31);

        assertThat(docstrings, hasItems("A fieldStatic", "A methodStatic"));
    }

    @Test
    public void classes() throws IOException {
        String file = "/org/javacs/example/AutocompleteClasses.java";

        // Static method
        Set<String> suggestions = insertText(file, 5, 10);

        assertThat(suggestions, hasItems("String", "SomeInnerClass"));
    }

    @Test
    public void editMethodName() throws IOException {
        String file = "/org/javacs/example/AutocompleteEditMethodName.java";

        // Static method
        Set<String> suggestions = insertText(file, 5, 21);

        assertThat(suggestions, hasItems("getClass"));
    }

    @Test
    public void restParams() throws IOException {
        String file = "/org/javacs/example/AutocompleteRest.java";

        // Static method
        Set<String> suggestions = items(file, 5, 18).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("restMethod(String... params)"));
    }

    @Test
    public void constructor() throws IOException {
        String file = "/org/javacs/example/AutocompleteConstructor.java";

        // Static method
        Set<String> suggestions = insertText(file, 5, 17);

        assertThat(suggestions, hasItems("AutocompleteConstructor", "AutocompleteMember", "String"));
    }

    @Test
    public void importPackage() throws IOException {
        String file = "/org/javacs/example/AutocompletePackage.java";

        // Static method
        Set<String> suggestions = insertText(file, 3, 12);

        assertThat("Has next package", suggestions, hasItems("javacs"));
        assertThat("Has deeply nested class", suggestions, hasItems("javacs.example.AutocompleteMember"));
    }

    @Test
    public void importClass() throws IOException {
        String file = "/org/javacs/example/AutocompletePackage.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 27);

        assertThat(suggestions, hasItems("AutocompleteMember"));
    }

    @Test
    public void fieldFromStaticInner() throws IOException {
        String file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of static inner class
        Set<String> suggestions = insertText(file, 12, 14);

        assertThat(suggestions, hasItems("methodStatic", "fieldStatic"));
        assertThat(suggestions, not(hasItems("method", "field")));
    }

    @Test
    public void fieldFromInner() throws IOException {
        String file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of inner class
        Set<String> suggestions = insertText(file, 18, 14);

        assertThat(suggestions, hasItems("methodStatic", "fieldStatic"));
        assertThat(suggestions, hasItems("method", "field"));
    }

    @Test
    public void classDotClassFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        // AutocompleteInners.I
        Set<String> suggestions = insertText(file, 5, 29);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerClassFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        // I
        Set<String> suggestions = insertText(file, 6, 10);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void newClassDotInnerClassFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        // new AutocompleteInners.I
        Set<String> suggestions = insertText(file, 10, 33);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void newInnerClassFromMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        // new I
        Set<String> suggestions = insertText(file, 11, 14);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerEnum() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        Set<String> suggestions = insertText(file, 15, 310);

        assertThat("suggests enum constants", suggestions, hasItems("Foo", "Bar"));
    }
}
