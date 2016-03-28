package org.javacs.message;

import java.util.Objects;

public class Range {
    public static final Range NONE = new Range(Position.NONE, Position.NONE);
    /**
     * The start position. It is before or equal to [end](#Range.end).
     */
    public final Position start;

    /**
     * The end position. It is after or equal to [start](#Range.start).
     */
    public final Position end;

    public Range(Position start, Position end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return Objects.equals(start, range.start) &&
               Objects.equals(end, range.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return start + ":" + end;
    }
}
