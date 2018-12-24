package org.javacs;

import java.time.Instant;
import java.util.List;

public class Index {
    public static final Index EMPTY = new Index(List.of(), Instant.EPOCH);

    public final List<Ref> refs;
    // TODO modified time can rewind when you switch branches, need to track modified and look for exact match
    public final Instant created;

    public Index(List<Ref> refs, Instant created) {
        this.refs = refs;
        this.created = created;
    }
}
