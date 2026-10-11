package org.mtr.mod.render;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Stable selection cache with bounded residency and one construction per frame. */
public final class RailSectorCache<K, V, R extends AutoCloseable> implements AutoCloseable {
    private final Map<K, Entry<V, R>> entries = new LinkedHashMap<>(64, 0.75F, true);
    private final int maxEntries, maxVertices, maxBuildVertices;
    private long frame;
    private int residentVertices, buildsLeft;
    private static final class Entry<V, R> {
        List<V> selection;
        R resource;
        long seen;
        int vertices, stable;
        boolean failed;
    }

    public RailSectorCache(int maxEntries, int maxVertices, int maxBuildVertices) {
        this.maxEntries = maxEntries;
        this.maxVertices = maxVertices;
        this.maxBuildVertices = maxBuildVertices;
    }

    public void beginFrame() { frame++; buildsLeft = 1; }

    public R get(K key, List<V> selection, int vertices, Supplier<R> builder) {
        if (selection.size() < 4 || vertices <= 0 || vertices > maxBuildVertices || vertices > maxVertices) return null;
        Entry<V, R> entry = entries.get(key);
        if (entry != null && entry.resource != null && entry.seen == frame && !selection.equals(entry.selection)) return null;
        if (entry == null) {
            if (!makeRoom(null, 1, 0)) return null;
            entry = new Entry<>();
            entries.put(key, entry);
        }
        if (!selection.equals(entry.selection)) {
            release(entry);
            entry.selection = List.copyOf(selection);
            entry.stable = 1;
            entry.failed = false;
        } else if (entry.seen == frame - 1) {
            entry.stable = Math.min(2, entry.stable + 1);
        } else if (entry.seen != frame) {
            entry.stable = 1;
        }
        entry.seen = frame;
        if (entry.resource != null) return entry.resource;
        if (entry.stable < 2 || entry.failed || buildsLeft == 0 || !makeRoom(entry, 0, vertices)) return null;
        buildsLeft--;
        try {
            entry.resource = builder.get();
            if (entry.resource != null) {
                entry.vertices = vertices;
                residentVertices += vertices;
            } else entry.failed = true;
            return entry.resource;
        } catch (RuntimeException error) {
            entry.failed = true;
            throw error;
        }
    }

    private boolean makeRoom(Entry<V, R> keep, int newEntries, int vertices) {
        final Iterator<Entry<V, R>> iterator = entries.values().iterator();
        while ((entries.size() + newEntries > maxEntries || residentVertices + vertices > maxVertices) && iterator.hasNext()) {
            final Entry<V, R> victim = iterator.next();
            // A resource already queued in this frame cannot be disposed before drawAll.
            if (victim == keep || victim.seen == frame) continue;
            release(victim);
            iterator.remove();
        }
        return entries.size() + newEntries <= maxEntries && residentVertices + vertices <= maxVertices;
    }

    private void release(Entry<V, R> entry) {
        if (entry.resource == null) return;
        try { entry.resource.close(); }
        catch (Exception error) { throw new IllegalStateException("Failed to dispose a rail sector", error); }
        finally { residentVertices -= entry.vertices; entry.vertices = 0; entry.resource = null; }
    }

    public int size() { return entries.size(); }
    public int residentVertices() { return residentVertices; }
    @Override public void close() {
        entries.values().forEach(this::release);
        entries.clear();
        residentVertices = 0;
    }
}
