package com.fivetran.javac.message;

import java.util.Objects;

public class Position {
    public final long line, character;

    public Position(long line, long character) {
        if (line < 0)
            throw new IllegalArgumentException("Line " + line + " is < 0");
        if (character < 0)
            throw new IllegalArgumentException("Character " + character + " is < 0");
            
        this.line = line;
        this.character = character;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return line == position.line &&
               character == position.character;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, character);
    }

    @Override
    public String toString() {
        return "Position{" +
               "line=" + line +
               ", character=" + character +
               '}';
    }
}
