package com.fivetran.javac.message;

public class LintMessage {
    public final Type type;
    public final String message, filePath;
    public final Range range;

    public LintMessage(Type type, String message, String filePath, Range range) {
        this.type = type;
        this.message = message;
        this.filePath = filePath;
        this.range = range;
    }

    public static enum Type {
        Error,
        Warning
    }
}
