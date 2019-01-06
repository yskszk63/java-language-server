package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Cache maps a file + an arbitrary key to a value. When the file is modified, the mapping expires. */
class Cache<K, V> {
    private class Key {
        final Path file;
        final K key;

        Key(Path file, K key) {
            this.file = file;
            this.key = key;
        }

        @Override
        public boolean equals(Object other) {
            if (other.getClass() != Key.class) return false;
            var that = (Key) other;
            return Objects.equals(this.key, that.key) && Objects.equals(this.file, that.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, key);
        }
    }

    private class Value {
        final V value;
        final Instant created = Instant.now();

        Value(V value) {
            this.value = value;
        }
    }

    private final Map<Key, Value> map = new HashMap<>();

    boolean needs(Path file, K key) {
        // If key is not in map, it needs to be loaded
        if (!map.containsKey(key)) return true;

        // If key was loaded before file was last modified, it needs to be reloaded
        var value = map.get(key);
        Instant modified;
        try {
            modified = Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return value.created.isBefore(modified);
    }

    void load(Path file, K key, V value) {
        // TODO limit total size of cache
        map.put(new Key(file, key), new Value(value));
    }

    V get(Path file, K key) {
        var k = new Key(file, key);
        if (!map.containsKey(k)) {
            throw new IllegalArgumentException(k + " is not in map " + map);
        }
        return map.get(k).value;
    }

    void clear() {
        map.clear();
    }
}
