package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.MtrRenderBridge;
import org.mtr.mod.render.MainRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Real upstream MTR 4.0.5 integration. No copy of MTR's MainRenderer,
 * no replacement modId, and no shader bytecode modification.
 */
@Mixin(value=MainRenderer.class, remap=false)
public abstract class MtrMainPassMixin {
    @Inject(method="render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V",
            at=@At("HEAD"), remap=false)
    private static void mtrengine$startPass(CallbackInfo ci){
        MtrRenderBridge.onFrameStart();
    }

    @Inject(method="render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V",
            at=@At("RETURN"), remap=false)
    private static void mtrengine$endPass(CallbackInfo ci){
        MtrRenderBridge.onFrameEnd();
    }
}
