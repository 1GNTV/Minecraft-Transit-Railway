package org.mtr.mod.mixin;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mod.Init;
import org.mtr.mod.render.OpaqueMeshCombiner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Pseudo
@Mixin(targets = "org.mtr.mapping.mapper.OptimizedModel", remap = false)
public abstract class OptimizedObjModelMixin {

	@Shadow @Final private static VertexAttributeMapping DEFAULT_MAPPING;
	@Unique private static final boolean mtr$mergeObj = !"false".equalsIgnoreCase(System.getProperty("mtr.v4.mergeOpaqueObj", "true"));
	@Unique private static final boolean mtr$metrics = Boolean.getBoolean("mtr.v4.metrics");

	@Inject(method = "fromObjModels", at = @At("HEAD"), cancellable = true)
	private static void mtr$combineSolidMeshes(Collection<OptimizedModel.ObjModel> models, CallbackInfoReturnable<OptimizedModel> cir) {
		if (!mtr$mergeObj || models.size() < 2) return;
		final OpaqueMeshCombiner combiner = new OpaqueMeshCombiner();
		for (OptimizedModel.ObjModel model : models) {
			final RawModel rawModel = ((OptimizedObjModelAccessor) (Object) model).mtr$getRawModel();
			// Preserve per-source normal generation before combining unrelated OBJ parts.
			rawModel.generateNormals();
			rawModel.distinct();
			rawModel.iterateRawMeshList(combiner::append);
		}
		final List<VertexArray> uploaded = new ArrayList<>();
		try {
			for (RawMesh mesh : combiner.meshes()) {
				uploaded.add(new VertexArray(mesh.upload(DEFAULT_MAPPING), DEFAULT_MAPPING));
			}
		} catch (RuntimeException | Error error) {
			uploaded.forEach(VertexArray::close);
			throw error;
		}
		if (mtr$metrics) {
			Init.LOGGER.info("MTR V4 OBJ upload: objects={}, source-draws={}, uploaded-draws={}", models.size(), combiner.sourceDraws(), uploaded.size());
		}
		cir.setReturnValue(new OptimizedModel(uploaded));
	}
}
