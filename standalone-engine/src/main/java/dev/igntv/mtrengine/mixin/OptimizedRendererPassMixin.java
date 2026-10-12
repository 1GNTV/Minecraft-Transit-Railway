package dev.igntv.mtrengine.mixin;

import org.mtr.mapping.render.batch.BatchManager;
import org.mtr.mapping.render.shader.ShaderManager;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.tool.GlStateTracker;
import dev.igntv.mtrengine.renderer.GlVertexAttributeCache;
import dev.igntv.mtrengine.renderer.RenderPipelineMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.mtr.mapping.mapper.OptimizedRenderer", remap = false)
public abstract class OptimizedRendererPassMixin {

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/render/batch/BatchManager;drawAll(Lorg/mtr/mapping/render/shader/ShaderManager;Z)V"))
	private void mtr$drawProtectedPass(BatchManager manager, ShaderManager shaders, boolean translucent) {
		final boolean shaderless = Utilities.canUseCustomShader();
		GlVertexAttributeCache.begin(shaderless);
		RenderPipelineMetrics.begin(shaderless);
		try {
			manager.drawAll(shaders, translucent);
		} catch (RuntimeException | Error error) {
			// The original render method restores after drawAll, but skips it on failure.
			GlStateTracker.restore();
			throw error;
		} finally {
			GlVertexAttributeCache.end();
			RenderPipelineMetrics.end();
		}
	}
}
