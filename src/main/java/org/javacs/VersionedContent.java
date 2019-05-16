package org.javacs;

import java.time.Instant;

class VersionedContent {
    final String content;
    final int version;
    final Instant modified = Instant.now();

    VersionedContent(String content, int version) {
        this.content = content;
        this.version = version;
    }

    static final VersionedContent EMPTY = new VersionedContent("", -1);
}
