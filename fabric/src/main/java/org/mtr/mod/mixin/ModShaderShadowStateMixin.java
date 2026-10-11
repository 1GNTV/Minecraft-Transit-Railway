package org.mtr.mod.mixin;

import org.mtr.mod.render.ShadowPassState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.shader.ModShaderHandler", remap = false)
public abstract class ModShaderShadowStateMixin {
    @Inject(method = "renderingShadows", at = @At("HEAD"), cancellable = true)
    private static void mtr$oculusShadowState(CallbackInfoReturnable<Boolean> ci) {
        Boolean shadows = ShadowPassState.current();
        if (shadows != null) ci.setReturnValue(shadows);
    }
}
