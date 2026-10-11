package org.mtr.mod.render;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Render-frame lifetime, identity keys and revisions; never reuses a previous frame's pose. */
public final class FrameMemoizer<K, V> {
    private final Map<K, Entry<V>> entries = new IdentityHashMap<>();
    private final int capacity;
    private boolean active;
    private record Entry<V>(long revision, V value) {}
    public FrameMemoizer(int capacity) { if (capacity < 1) throw new IllegalArgumentException(); this.capacity = capacity; }
    public void beginFrame() { entries.clear(); active = true; }
    public void endFrame() { entries.clear(); active = false; }
    public V get(K key, long revision, Supplier<V> factory) {
        if (!active) return factory.get();
        Entry<V> entry = entries.get(key);
        if (entry != null && entry.revision == revision) return entry.value;
        V value = factory.get();
        if (entry != null || entries.size() < capacity) entries.put(key, new Entry<>(revision, value));
        return value;
    }
    public int size() { return entries.size(); }
}
