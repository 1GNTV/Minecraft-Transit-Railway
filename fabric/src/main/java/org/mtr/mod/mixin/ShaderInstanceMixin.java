package org.mtr.mod.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.mtr.mod.render.GlVertexAttributeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla and Oculus ShaderInstance.apply() is expensive when MTR re-applies
 * the full shader for every model-view matrix in a single material batch.
 *
 * Only suppress that invocation while VertexAttributeState.apply is active,
 * and only when the same shader program was validated at batch setup.
 * This avoids touching every other ShaderInstance.apply() in the game.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void mtr$v3UploadMatrixInstead(CallbackInfo ci) {
        if (GlVertexAttributeCache.uploadModelViewOnly((ShaderInstance) (Object) this)) {
            ci.cancel();
        }
    }
}
