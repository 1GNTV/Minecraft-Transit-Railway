package org.mtr.mod.render;

import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.vertex.VertexAttributeType;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.mixin.OptimizedModelLayoutAccessor;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.RailResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Experimental shaderless static rail batches. The original per-segment selection is retained. */
public final class RailSectorBatcher {
    private static final RailSectorCache<Sector, Selection, OwnedSector> CACHE = new RailSectorCache<>(64, 256_000, 16_000);
    private static final Map<Sector, List<Instance>> GROUPS = new LinkedHashMap<>();
    private static boolean active;
    private static Object world;
    private record Sector(int x, int y, int z) {
        static Sector at(double x, double y, double z) { return new Sector((int) Math.floor(x / 16), (int) Math.floor(y / 16), (int) Math.floor(z / 16)); }
    }
    private record Selection(OptimizedModelWrapper model, double x, double y, double z, float yaw, float pitch, float roll, int light) {}
    private record Instance(Selection selection, RailGeometrySnapshots.Snapshot snapshot, RailResource resource, StoredMatrixTransformations fallback) {}
    private static final class OwnedSector implements AutoCloseable {
        final List<VertexArray> arrays = new ArrayList<>();
        final List<Mesh> meshes = new ArrayList<>();
        OptimizedModelWrapper model;
        @Override public void close() { arrays.forEach(VertexArray::close); meshes.forEach(Mesh::close); arrays.clear(); meshes.clear(); }
    }
    private RailSectorBatcher() {}

    public static void beginFrame() {
        if (!RailGeometrySnapshots.ENABLED) return;
        GROUPS.clear();
        final Object currentWorld = MinecraftClient.getInstance().getWorldMapped().data;
        final boolean compatible = OptimizedRenderer.hasOptimizedRendering() && !OptimizedRenderer.renderingShadows() && Utilities.canUseCustomShader();
        if (currentWorld != world || (!compatible && active)) CACHE.close();
        world = currentWorld;
        active = compatible;
        if (active) CACHE.beginFrame();
    }

    /** Returns false when the ordinary rail resource must render this segment. */
    public static boolean queue(RailResource resource, StoredMatrixTransformations fallback, double x, double y, double z, float yaw, float pitch, float roll, int light) {
        if (!active) return false;
        final OptimizedModelWrapper model = resource.getOptimizedModel();
        if (model == null || model.getModel() == null) return false;
        final RailGeometrySnapshots.Snapshot snapshot = RailGeometrySnapshots.get(model.getModel());
        if (snapshot == null) return false;
        GROUPS.computeIfAbsent(Sector.at(x, y, z), key -> new ArrayList<>()).add(new Instance(new Selection(model, x, y, z, yaw, pitch, roll, light), snapshot, resource, fallback));
        return true;
    }

    public static void flush() {
        if (!active) return;
        try {
            GROUPS.forEach((sector, instances) -> {
                final List<Selection> selections = new ArrayList<>(instances.size());
                int vertices = 0;
                for (Instance instance : instances) { selections.add(instance.selection()); vertices += instance.snapshot().vertices(); }
                OwnedSector compiled = null;
                try { compiled = CACHE.get(sector, selections, vertices, () -> build(sector, instances)); }
                catch (RuntimeException error) { Init.LOGGER.error("MTR Work rail sector {} reverted to individual rendering", sector, error); }
                if (compiled == null) {
                    instances.forEach(instance -> instance.resource().render(instance.fallback(), instance.selection().light()));
                } else {
                    final OptimizedModelWrapper batch = compiled.model;
                    final double x = sector.x() * 16D, y = sector.y() * 16D, z = sector.z() * 16D;
                    MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> {
                        graphicsHolder.push();
                        try {
                            graphicsHolder.translate(x - offset.getXMapped(), y - offset.getYMapped(), z - offset.getZMapped());
                            CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(batch, graphicsHolder, 0, RenderPipelineMetrics.Family.RAIL);
                        } finally { graphicsHolder.pop(); }
                    });
                }
            });
        } finally { GROUPS.clear(); }
    }

    private static OwnedSector build(Sector sector, List<Instance> instances) {
        final OpaqueMeshCombiner combiner = new OpaqueMeshCombiner();
        for (Instance instance : instances) {
            final Selection s = instance.selection();
            for (RawMesh source : instance.snapshot().meshes()) combiner.append(RailBatchGeometry.bake(source, s.x() - sector.x() * 16D, s.y() - sector.y() * 16D, s.z() - sector.z() * 16D, s.yaw(), s.pitch(), s.roll(), s.light()));
        }
        // Preserve every other original source choice. Light is baked per segment.
        final VertexAttributeMapping.Builder builder = new VertexAttributeMapping.Builder();
        OptimizedModelLayoutAccessor.mtr$getDefaultMapping().sources.forEach(builder::set);
        final VertexAttributeMapping mapping = builder.set(VertexAttributeType.UV_LIGHTMAP, VertexAttributeSource.VERTEX_BUFFER).build();
        final OwnedSector result = new OwnedSector();
        // capture/restore only: beginReload would unnecessarily recompile shaders here.
        GlStateTracker.capture();
        try {
            for (RawMesh raw : combiner.meshes()) {
                final Mesh mesh = raw.upload(mapping);
                result.meshes.add(mesh);
                result.arrays.add(new VertexArray(mesh, mapping));
            }
            result.model = OptimizedModelWrapper.fromUploadedModel(new OptimizedModel(result.arrays));
        } catch (RuntimeException | Error error) {
            result.close();
            throw error;
        } finally { GlStateTracker.restore(); }
        if (Boolean.getBoolean("mtr.v4.metrics")) Init.LOGGER.info("MTR Work rail sector {}: segments={}, source-draws={}, uploaded-draws={}", sector, instances.size(), combiner.sourceDraws(), result.arrays.size());
        return result;
    }

    public static void clear() { CACHE.close(); GROUPS.clear(); active = false; world = null; }
    public static void reload() { clear(); RailGeometrySnapshots.clear(); }
}
