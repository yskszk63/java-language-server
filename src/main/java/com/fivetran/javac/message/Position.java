package com.fivetran.javac.message;

public class Position {
    public final long line, character;

    public Position(long line, long character) {
        this.line = line;
        this.character = character;
    }
}
