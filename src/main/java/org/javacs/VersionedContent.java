package org.javacs;

class VersionedContent {
    final String content;
    final int version;

    VersionedContent(String content, int version) {
        this.content = content;
        this.version = version;
    }

    static final VersionedContent EMPTY = new VersionedContent("", -1);
}
