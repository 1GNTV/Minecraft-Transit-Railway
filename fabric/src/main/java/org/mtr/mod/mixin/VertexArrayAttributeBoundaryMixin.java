package org.mtr.mod.mixin;

import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.object.IndexBuffer;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mod.render.GpuMeshOwnership;
import org.mtr.mod.render.GlVertexAttributeCache;
import org.mtr.mod.render.RenderPipelineMetrics;
import org.mtr.mod.render.VertexArrayIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.object.VertexArray", remap = false)
public abstract class VertexArrayAttributeBoundaryMixin implements VertexArrayIdentity {
    @Shadow private int id;
    @Override public int mtr$vaoId() { return id; }

	@Shadow @Final public VertexAttributeMapping mapping;
	@Shadow @Final public IndexBuffer indexBuffer;

	@Inject(method = "<init>(Lorg/mtr/mapping/render/object/VertexArray;Lorg/mtr/mapping/render/batch/MaterialProperties;)V", at = @At("RETURN"))
	private void mtr$shareWorkBuffers(VertexArray original, MaterialProperties material, CallbackInfo ci) {
		GpuMeshOwnership.alias((VertexArray) (Object) this, original);
	}

	@Inject(method = "close", at = @At("RETURN"))
	private void mtr$releaseWorkBuffers(CallbackInfo ci) {
		GpuMeshOwnership.close((VertexArray) (Object) this);
	}

	@Inject(method = "draw", at = @At("RETURN"))
	private void mtr$afterDraw(CallbackInfo ci) {
		if (GlVertexAttributeCache.isActive()) {
			mapping.sources.forEach((type, source) -> {
				if (source == VertexAttributeSource.VERTEX_BUFFER || source == VertexAttributeSource.INSTANCE_BUFFER) {
					GlVertexAttributeCache.invalidate(type.location, type.span);
				}
			});
		}
		RenderPipelineMetrics.draw(indexBuffer.getVertexCount());
	}
}
