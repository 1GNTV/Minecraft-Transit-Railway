package org.mtr.mod.mixin;

import org.mtr.mod.render.GlVertexAttributeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A ShaderInstance is already activated at the end of setupShaderBatchState.
 * Validate the actual bound GL program once, instead of at every model draw.
 */
@Pseudo
@Mixin(targets = "org.mtr.mapping.render.shader.ShaderManager", remap = false)
public abstract class ShaderManagerMixin {
    @Inject(method = "setupShaderBatchState", at = @At("RETURN"))
    private void mtr$v3BeginMaterial(CallbackInfo ci) {
        GlVertexAttributeCache.shaderBatchReady();
    }
}
