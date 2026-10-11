package org.mtr.mod.render;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.CapturingVertexConsumer;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mod.Init;
import org.mtr.mod.mixin.OptimizedMaterialGroupAccessor;
import org.mtr.mod.mixin.OptimizedModelLayoutAccessor;
import org.mtr.mod.mixin.OptimizedObjModelAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** One upload transaction for compatible Blockbench and OBJ parts of one condition. */
public final class ModelMeshCompiler {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.v4.mergeMixedModels", "true"));
    private ModelMeshCompiler() {}

    public static boolean available(Collection<OptimizedModel.MaterialGroup> groups, Collection<OptimizedModel.ObjModel> objects) {
        if (!ENABLED || groups.isEmpty() || objects.isEmpty()) return false;
        return accessorsAvailable(groups, objects);
    }

    public static boolean accessorsAvailable(Collection<OptimizedModel.MaterialGroup> groups, Collection<OptimizedModel.ObjModel> objects) {
        for (Object group : groups) if (!(group instanceof OptimizedMaterialGroupAccessor)) return false;
        for (Object object : objects) if (!(object instanceof OptimizedObjModelAccessor)) return false;
        return true;
    }

    public static OptimizedModel compile(Collection<OptimizedModel.MaterialGroup> groups, Collection<OptimizedModel.ObjModel> objects) {
        return compile(groups, objects, false);
    }

    public static OptimizedModel compile(Collection<OptimizedModel.MaterialGroup> groups, Collection<OptimizedModel.ObjModel> objects, boolean retainRailGeometry) {
        final CapturingVertexConsumer capture = new CapturingVertexConsumer();
        for (Object group : groups) {
            final OptimizedMaterialGroupAccessor accessor = (OptimizedMaterialGroupAccessor) group;
            capture.beginStage(accessor.mtr$getMaterial());
            accessor.mtr$getConsumers().forEach(consumer -> consumer.accept(capture));
        }
        capture.rawModel.triangulate();
        capture.rawModel.distinct();
        final OpaqueMeshCombiner combiner = new OpaqueMeshCombiner();
        capture.rawModel.iterateRawMeshList(combiner::append);
        for (Object object : objects) {
            final RawModel model = ((OptimizedObjModelAccessor) object).mtr$getRawModel();
            // Keep the original per-OBJ normals/distinct semantics before consolidation.
            model.generateNormals();
            model.distinct();
            model.iterateRawMeshList(combiner::append);
        }
        final VertexAttributeMapping mapping = OptimizedModelLayoutAccessor.mtr$getDefaultMapping();
        final List<VertexArray> arrays = new ArrayList<>();
        final List<Mesh> meshes = new ArrayList<>();
        try {
            for (RawMesh raw : combiner.meshes()) {
                final Mesh mesh = OpaqueMeshOptimizer.optimize(raw).upload(mapping);
                meshes.add(mesh);
                arrays.add(new VertexArray(mesh, mapping));
            }
        } catch (RuntimeException | Error error) {
            arrays.forEach(VertexArray::close);
            meshes.forEach(Mesh::close);
            throw error;
        }
        if (Boolean.getBoolean("mtr.v4.metrics")) {
            Init.LOGGER.info("MTR Work mixed upload: blockbench-groups={}, obj-parts={}, source-draws={}, uploaded-draws={}", groups.size(), objects.size(), combiner.sourceDraws(), arrays.size());
        }
        final OptimizedModel model = new OptimizedModel(arrays);
        for (int i = 0; i < arrays.size(); i++) GpuMeshOwnership.attach(arrays.get(i), meshes.get(i));
        if (retainRailGeometry) RailGeometrySnapshots.retain(model, combiner.meshes());
        return model;
    }
}
