package org.mtr.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import org.mtr.mod.render.GlVertexAttributeCache;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager", remap = false)
public abstract class OptimizedBatchManagerMixin {

	@Shadow @Final private Map<?, ?> translucentBatches;


	// Cache only during MTR batching; the outside GL state may be changed by other mods.
	@Inject(method = "drawAll", at = @At("HEAD"))
	private void mtr$beginAttributePass(CallbackInfo ci) {
		GlVertexAttributeCache.begin();
	}

	@Inject(method = "drawAll", at = @At("RETURN"))
	private void mtr$endAttributePass(CallbackInfo ci) {
		GlVertexAttributeCache.end();
	}

	// drawAll(false) skips both drawing and clearing the translucent queue.
	@Inject(method = "drawAll", at = @At("RETURN"))
	private void mtr$discardUndrawnTranslucent(CallbackInfo ci) {
		translucentBatches.clear();
	}
}
