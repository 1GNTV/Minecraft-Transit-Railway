package org.mtr.mod.mixin;

import org.mtr.mod.render.GlVertexAttributeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppress identical VAO binds only inside a protected MTR render batch.
 * ShaderManager mixin invalidates on each material batch transition.
 */
@Pseudo
@Mixin(targets = "org.mtr.mapping.render.object.VertexArray", remap = false)
public abstract class VertexArrayMixin {
    @Redirect(method = "bind", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33;glBindVertexArray(I)V"))
    private void mtr$v3BindOnce(int vao) {
        GlVertexAttributeCache.bindVertexArray(vao);
    }
}
