package org.javacs.example

public class AutocompleteMembers {
    private String field;
    private static String fieldStatic;

    {
        f; // field, fieldStatic
        m; // method, methodStatic
        a; //
        this.f; // field
        this.m; // method
        AutocompleteMembers.f; // fieldStatic
        AutocompleteMembers.m; // methodStatic
        this::m; // method
        AutocompleteMembers::m; // methodStatic
    }

    static {
        f; // fieldStatic
        m; // methodStatic
        a; //
        this.f; //
        this.m; //
        AutocompleteMembers.f; // fieldStatic
        AutocompleteMembers.m; // methodStatic
        this::m; //
        AutocompleteMembers::m; // methodStatic
    }

    private void method(String argument) {
        f; // field, fieldStatic
        m; // method, methodStatic
        a; // argument
        this.f; // field
        this.m; // method
        AutocompleteMembers.f; // fieldStatic
        AutocompleteMembers.m; // methodStatic
        this::m; // method
        AutocompleteMembers::m; // methodStatic
    }

    private static void methodStatic(String argument) {
        f; // fieldStatic
        m; // methodStatic
        a; // argument
        this.f; //
        this.m; //
        AutocompleteMembers.f; // fieldStatic
        AutocompleteMembers.m; // methodStatic
        this::m; //
        AutocompleteMembers::m; // methodStatic
    }
}