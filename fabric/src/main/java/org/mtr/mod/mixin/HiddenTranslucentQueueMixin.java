package org.mtr.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager", remap = false)
public abstract class HiddenTranslucentQueueMixin {
	@Shadow @Final private Map<?, ?> translucentBatches;

	@Inject(method = "drawAll", at = @At("RETURN"))
	private void mtr$discardHiddenTranslucent(CallbackInfo ci) {
		// Hidden geometry must not accumulate and appear later with stale train matrices.
		translucentBatches.clear();
	}
}
