package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class Cache<K, V> {
    private class Entry {
        final V value;
        final Instant created = Instant.now();

        Entry(V value) {
            this.value = value;
        }
    }

    private Map<K, Entry> map = new HashMap<>();

    private final Function<K, V> loader;

    private final Function<K, Path> asFile;

    Cache(Function<K, V> loader, Function<K, Path> asFile) {
        this.loader = loader;
        this.asFile = asFile;
    }

    private void load(K key) {
        // TODO limit total size of cache
        var value = loader.apply(key);
        map.put(key, new Entry(value));
    }

    V get(K key) {
        // Check if file is missing from cache
        if (!map.containsKey(key)) load(key);

        // Check if file is out-of-date
        var value = map.get(key);
        var file = asFile.apply(key);
        Instant modified;
        try {
            modified = Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (value.created.isBefore(modified)) load(key);

        // Get up-to-date file from cache
        return map.get(key).value;
    }
}
