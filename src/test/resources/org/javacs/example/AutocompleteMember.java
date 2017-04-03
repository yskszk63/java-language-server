package org.javacs.example;

public class AutocompleteMember {
    public void test() {
        this.;
    }

    public static String fieldStatic;
    public String fields;
    public static String methodStatic() {
        return "foo";
    }
    public String methods() throws Exception {
        return "foo";
    }

    private static String fieldStaticPrivate;
    private String fieldsPrivate;
    private static String methodStaticPrivate() {
        return "foo";
    }
    private String methodsPrivate() {
        return "foo";
    }
}