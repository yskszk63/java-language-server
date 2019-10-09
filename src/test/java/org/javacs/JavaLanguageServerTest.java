package org.javacs;

import org.junit.Test;

public class JavaLanguageServerTest {
    @Test
    public void isMemberSelect() {
        String[][] examples = {
            {"foo.bar", "bar"},
            {"foo.", ""},
        };
        for (var test : examples) {
            var example = test[0];
            var expect = test[1];
            var start = JavaLanguageServer.isMemberSelect(example, example.length());
            assert start != -1 : expect + " is not selected in " + example;
            var found = example.substring(start + 1);
            assert found.equals(expect) : found + " not " + expect + " is selected in " + example;
        }
    }

    @Test
    public void isNotMemberSelect() {
        String[] examples = {
            "foo::bar", "foo::", "foo", "",
        };
        for (var test : examples) {
            var start = JavaLanguageServer.isMemberSelect(test, test.length());
            assert start == -1 : test.substring(start + 1) + " is selected in " + test;
        }
    }

    @Test
    public void isMemberReference() {
        String[][] examples = {
            {"foo::bar", "bar"},
            {"foo::", ""},
        };
        for (var test : examples) {
            var example = test[0];
            var expect = test[1];
            var start = JavaLanguageServer.isMemberReference(example, example.length());
            assert start != -1 : expect + " is not referenced in " + example;
            var found = example.substring(start + 2);
            assert found.equals(expect) : found + " not " + expect + " is referenced in " + example;
        }
    }

    @Test
    public void isNotMemberReference() {
        String[] examples = {
            "foo.bar", "foo.", "foo", "",
        };
        for (var test : examples) {
            var start = JavaLanguageServer.isMemberReference(test, test.length());
            assert start == -1 : test.substring(start + 1) + " is selected in " + test;
        }
    }

    @Test
    public void isPartialAnnotation() {
        String[][] examples = {
            {"@Foo", "Foo"},
            {"@com.foo.Bar", "com.foo.Bar"},
            {"@", ""},
        };
        for (var test : examples) {
            var example = test[0];
            var expect = test[1];
            var start = JavaLanguageServer.isPartialAnnotation(example, example.length());
            assert start != -1 : expect + " is not referenced in " + example;
            var found = example.substring(start + 1);
            assert found.equals(expect) : found + " not " + expect + " is referenced in " + example;
        }
    }

    @Test
    public void isNotPartialAnnotation() {
        String[] examples = {
            "foo.bar", "foo.", "foo", "",
        };
        for (var test : examples) {
            var start = JavaLanguageServer.isPartialAnnotation(test, test.length());
            assert start == -1 : test.substring(start + 1) + " is selected in " + test;
        }
    }

    @Test
    public void isPartialCase() {
        String[] examples = {
            "case foo", "case ", "case  foo",
        };
        for (var example : examples) {
            var is = JavaLanguageServer.isPartialCase(example, example.length());
            assert is : example + " is not a case";
        }
    }

    @Test
    public void isNotPartialCase() {
        String[] examples = {
            "case", "", "foo",
        };
        for (var example : examples) {
            var is = JavaLanguageServer.isPartialCase(example, example.length());
            assert !is : example + " is a case";
        }
    }

    @Test
    public void partialName() {
        String[][] examples = {
            {"foo", "foo"},
            {"foo.bar", "bar"},
            {"foo.", ""},
            {"", ""},
        };
        for (var test : examples) {
            var example = test[0];
            var expect = test[1];
            var found = JavaLanguageServer.partialName(example, example.length());
            assert found.equals(expect) : found + " not " + expect + " is selected in " + example;
        }
    }
}
