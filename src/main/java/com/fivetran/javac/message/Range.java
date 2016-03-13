package com.fivetran.javac.message;

public class Range {
    public final Point start, end;

    public Range(Point start, Point end) {
        this.start = start;
        this.end = end;
    }
}
