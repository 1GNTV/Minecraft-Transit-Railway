package org.mtr.mod.mixin;

import org.mtr.mod.render.ShaderReloadPolicy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.shader.ShaderManager", remap = false)
public abstract class ShaderReloadGenerationMixin {
    @Shadow @Final private Map<String, ?> shaders;
    @Unique private long mtr$compilationGeneration;
    @Unique private boolean mtr$complete() {
        return shaders.size() == 3 && shaders.get("rendertype_entity_cutout") != null && shaders.get("rendertype_entity_translucent_cull") != null && shaders.get("rendertype_beacon_beam") != null;
    }
    @Inject(method = "reloadShaders", at = @At("HEAD"), cancellable = true)
    private void mtr$compileOnce(CallbackInfo ci) {
        mtr$compilationGeneration = ShaderReloadPolicy.generation();
        if (ShaderReloadPolicy.canSkip(this, mtr$complete())) ci.cancel();
    }
    @Inject(method = "reloadShaders", at = @At("RETURN"))
    private void mtr$compiled(CallbackInfo ci) {
        ShaderReloadPolicy.completed(this, mtr$compilationGeneration, mtr$complete());
    }
}
