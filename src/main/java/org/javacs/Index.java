package org.javacs;

import java.time.Instant;
import java.util.List;

public class Index {
    public static final Index EMPTY = new Index(List.of(), Instant.EPOCH);

    public final List<Ref> refs;
    public final Instant created;

    public Index(List<Ref> refs, Instant created) {
        this.refs = refs;
        this.created = created;
    }
}
