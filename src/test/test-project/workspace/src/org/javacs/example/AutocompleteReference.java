package org.javacs.example;

import java.util.function.Supplier;

public class AutocompleteReference {
    public void test() {
        print(this::)
    }

    private void print(Supplier<String> message) {
        System.out.println(message.get());
    }

    private static String fieldStatic;
    private String fields;
    private static String methodStatic() {
        return "foo";
    }
    private String methods() {
        return "foo";
    }
}