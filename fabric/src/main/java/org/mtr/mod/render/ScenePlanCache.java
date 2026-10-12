package org.mtr.mod.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * CPU-only registry for immutable render plans. A plan is built once per
 * location/revision and can survive camera and visibility changes. It must
 * never retain frame callbacks, live world wrappers or GPU ownership.
 *
 * Entries are LRU-bounded and world/resource-reload invalidation is explicit.
 * Evicting a plan only discards CPU metadata; its separately owned region
 * buffers remain controlled by PersistentRegionCache.
 */
public final class ScenePlanCache<K, R, V> {
    private record Entry<R, V>(R revision, V value) {}
    private final int capacity;
    private final Map<K, Entry<R, V>> plans;
    private long hits, misses;

    public ScenePlanCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.plans = new LinkedHashMap<>(Math.min(capacity, 1024), 0.75F, true);
    }

    public V resolve(K key, R revision, Supplier<V> build) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(build, "build");
        Entry<R, V> old = plans.get(key);
        if (old != null && Objects.equals(old.revision, revision)) {
            hits++;
            return old.value;
        }
        // Don't mutate the existing entry until construction has succeeded.
        // An exception must not install a partially prepared render plan.
        V value = Objects.requireNonNull(build.get(), "new scene plan");
        misses++;
        plans.put(key, new Entry<>(revision, value));
        while (plans.size() > capacity) {
            K eldest = plans.keySet().iterator().next();
            plans.remove(eldest);
        }
        return value;
    }

    public int size() { return plans.size(); }
    public long hits() { return hits; }
    public long misses() { return misses; }
    public void clear() {
        plans.clear();
        hits = misses = 0;
    }
}
