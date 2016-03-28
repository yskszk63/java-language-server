package org.javacs.message;

import java.net.URI;
import java.util.Objects;

public class Location {
    public final URI uri;
    public final Range range;

    public Location(URI uri, Range range) {
        this.uri = uri;
        this.range = range;
    }

    public Location(URI uri, int fromLine, int fromCharacter, int toLine, int toCharacter) {
        this(uri, new Range(new Position(fromLine, fromCharacter), new Position(toLine, toCharacter)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(uri, location.uri) &&
               Objects.equals(range, location.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, range);
    }

    @Override
    public String toString() {
        return uri + " " + range;
    }
}
