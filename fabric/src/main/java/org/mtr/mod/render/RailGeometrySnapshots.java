package org.mtr.mod.render;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawMesh;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Optional, bounded CPU copies of solid rail resources, indexed by model identity. */
public final class RailGeometrySnapshots {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.regions", "true"));
    private static final int MAX_SOURCE_VERTICES = 16_000;
    private static final int MAX_RETAINED_VERTICES = 200_000;
    private static final WeakHashMap<OptimizedModel, Snapshot> SNAPSHOTS = new WeakHashMap<>();
    public record Snapshot(List<RawMesh> meshes, int vertices) {}
    private RailGeometrySnapshots() {}

    public static void retain(OptimizedModel model, List<RawMesh> meshes) {
        if (!ENABLED || !RailBatchGeometry.eligible(meshes)) return;
        final int vertices = meshes.stream().mapToInt(mesh -> mesh.vertices.size()).sum();
        if (vertices > MAX_SOURCE_VERTICES || vertices <= 0) return;
        int retained = 0;
        for (Snapshot snapshot : SNAPSHOTS.values()) retained += snapshot.vertices();
        if (retained + vertices > MAX_RETAINED_VERTICES) return;
        final List<RawMesh> copies = new ArrayList<>();
        meshes.forEach(mesh -> copies.add(RailBatchGeometry.copy(mesh)));
        SNAPSHOTS.put(model, new Snapshot(List.copyOf(copies), vertices));
    }
    public static Snapshot get(OptimizedModel model) { return ENABLED ? SNAPSHOTS.get(model) : null; }
    public static void clear() { SNAPSHOTS.clear(); }
}
