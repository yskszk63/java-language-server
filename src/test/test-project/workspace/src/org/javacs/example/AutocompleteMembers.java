package org.javacs.example;

public class AutocompleteMembers {
    private String fields;
    private static String fieldStatic;

    {
        s; // fields, fieldStatic, methods, methodStatic
        this.s; // fields, methods
        AutocompleteMembers.s; // fieldStatic, methodStatic
        this::s; // methods
        AutocompleteMembers::s; // methods, methodStatic
    }

    static {
        s; // fieldStatic
        AutocompleteMembers.s; // fieldStatic
        AutocompleteMembers::s; // methods, methodStatic
    }

    private void methods(String arguments) {
        s; // fields, fieldStatic, methods, methodStatic, arguments
        this.s; // fields, methods
        AutocompleteMembers.s; // fieldStatic, methodStatic
        java.util.function.Supplier<String> test = this::s; // methods
        java.util.function.Supplier<String> test = AutocompleteMembers::s; // methods, methodStatic
    }

    private static void methodStatic(String arguments) {
        s; // fieldStatic, arguments
        AutocompleteMembers.s; // fieldStatic
        AutocompleteMembers::s; // methods, methodStatic
    }
}