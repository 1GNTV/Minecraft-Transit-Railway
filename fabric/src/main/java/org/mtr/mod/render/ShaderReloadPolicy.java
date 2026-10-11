package org.mtr.mod.render;

import java.util.Map;
import java.util.WeakHashMap;

/** Caches only successful shader compilation, never per-draw shader application. */
public final class ShaderReloadPolicy {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.shaderReloadCache", "true"));
    private static final Map<Object, Long> READY = new WeakHashMap<>();
    private static long generation;
    private ShaderReloadPolicy() {}
    public static synchronized long generation() { return generation; }
    public static synchronized void invalidate() { generation++; READY.clear(); }
    public static synchronized boolean canSkip(Object manager, boolean complete) {
        Long ready = READY.get(manager);
        return ENABLED && complete && ready != null && ready == generation;
    }
    public static synchronized void completed(Object manager, long startedGeneration, boolean complete) {
        if (complete && startedGeneration == generation) READY.put(manager, startedGeneration);
        else READY.remove(manager);
    }
}
