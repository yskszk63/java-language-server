package org.javacs;

import java.time.Instant;
import java.util.List;

public class Index {
    public static final Index EMPTY = new Index(List.of(), Instant.EPOCH, false);

    public final List<Ptr> refs;
    // TODO modified time can rewind when you switch branches, need to track modified and look for exact match
    public final Instant modified;
    public final boolean containsError;

    public Index(List<Ptr> refs, Instant modified, boolean containsError) {
        this.refs = refs;
        this.modified = modified;
        this.containsError = containsError;
    }
}
