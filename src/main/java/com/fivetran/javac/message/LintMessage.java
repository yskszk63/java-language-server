package com.fivetran.javac.message;

public class LintMessage {
    public final Type type;
    public final String text, filePath;
    public final Range range;

    public LintMessage(Type type, String text, String filePath, Range range) {
        this.type = type;
        this.text = text;
        this.filePath = filePath;
        this.range = range;
    }

    public static enum Type {
        Error,
        Warning
    }
}
