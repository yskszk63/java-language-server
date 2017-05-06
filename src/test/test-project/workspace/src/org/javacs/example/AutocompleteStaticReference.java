package org.javacs.example;

import java.util.function.Supplier;

public class AutocompleteStaticReference {
    public static void test() {
        print(AutocompleteStaticReference::)
    }

    private void print(Supplier<String> message) {
        System.out.println(message.get());
    }

    private static String fieldStatic;
    private String field;
    private static String methodStatic() {
        return "foo";
    }
    private String methods() {
        return "foo";
    }
}