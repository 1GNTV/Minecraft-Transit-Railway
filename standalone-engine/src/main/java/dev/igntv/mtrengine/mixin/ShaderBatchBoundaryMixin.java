package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.renderer.GlVertexAttributeCache;
import dev.igntv.mtrengine.renderer.RenderPipelineMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.shader.ShaderManager", remap = false)
public abstract class ShaderBatchBoundaryMixin {

	@Inject(method = {"setupShaderBatchState", "cleanupShaderBatchState"}, at = @At("HEAD"))
	private void mtr$beforeShaderState(CallbackInfo ci) {
		GlVertexAttributeCache.invalidate();
	}

	@Inject(method = {"setupShaderBatchState", "cleanupShaderBatchState"}, at = @At("RETURN"))
	private void mtr$afterShaderState(CallbackInfo ci) {
		GlVertexAttributeCache.invalidate();
	}

	@Inject(method = "setupShaderBatchState", at = @At("HEAD"))
	private void mtr$countMaterial(CallbackInfo ci) {
		RenderPipelineMetrics.material();
	}
}
