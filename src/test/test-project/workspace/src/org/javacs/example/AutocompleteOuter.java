package org.javacs.example;

public class AutocompleteOuter {
    public String fields;
    public static String fieldStatic;

    public String methods() { }
    public static String methodStatic() { }

    static class StaticInner {
        {
            s
        }
    }

    class Inner {
        {
            s
        }
    }
}