package org.mtr.mod.mixin;

import org.mtr.mod.render.InstancedModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager", remap = false)
public abstract class BatchInstancingMixin {
    @SuppressWarnings("unchecked")
    @Redirect(method = "lambda$drawBatch$2", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private static void mtr$instances(List<?> calls, Consumer<?> ordinary) {
        InstancedModelRenderer.draw(calls, (Consumer<Object>) ordinary);
    }
}
