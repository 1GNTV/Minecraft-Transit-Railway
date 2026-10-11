package org.mtr.mod.mixin;

import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mod.render.GpuMeshOwnership;
import org.mtr.mod.render.OpaqueMeshOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Includes original single-OBJ and Blockbench uploads without changing material grouping. */
@Pseudo
@Mixin(targets = "org.mtr.mapping.render.model.RawModel", remap = false)
public abstract class RawModelUploadMixin {
    @Redirect(method = "lambda$upload$0", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/render/model/RawMesh;upload(Lorg/mtr/mapping/render/vertex/VertexAttributeMapping;)Lorg/mtr/mapping/render/model/Mesh;"))
    private static Mesh mtr$prepareOpaque(RawMesh raw, VertexAttributeMapping mapping) {
        return OpaqueMeshOptimizer.optimize(raw).upload(mapping);
    }
    @Redirect(method = "lambda$upload$0", at = @At(value = "NEW", target = "org/mtr/mapping/render/object/VertexArray"))
    private static VertexArray mtr$ownBuffers(Mesh mesh, VertexAttributeMapping mapping) {
        VertexArray array = null;
        try {
            array = new VertexArray(mesh, mapping);
            GpuMeshOwnership.attach(array, mesh);
            return array;
        } catch (RuntimeException | Error failure) {
            if (array != null) array.close();
            mesh.close();
            throw failure;
        }
    }
}
