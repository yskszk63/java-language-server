package org.javacs;

class Artifact {
    final String groupId, artifactId, version;

    Artifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }
}