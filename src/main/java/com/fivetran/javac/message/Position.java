package com.fivetran.javac.message;

import java.util.Objects;

public class Position {
    public static final Position NONE = new Position(-1, -1);
    public int line, character;

    public Position() {
        this(-1, -1);
    }

    public Position(int line, int character) {
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
        return line + "." + character;
    }
}
