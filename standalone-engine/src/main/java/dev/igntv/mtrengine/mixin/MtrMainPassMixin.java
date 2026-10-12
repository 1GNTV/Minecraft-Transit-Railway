package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.MtrRenderBridge;
import dev.igntv.mtrengine.renderer.MtrRailModelBuilds;
import dev.igntv.mtrengine.renderer.RailSectorBatcher;
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
            at=@At(value="INVOKE",target="Lorg/mtr/mod/render/RenderRails;render()V"),remap=false)
    private static void mtrengine$railBefore(CallbackInfo ci) {
        if(MtrRailModelBuilds.ENABLED)RailSectorBatcher.beginFrame();
    }

    @Inject(method="render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V",
            at=@At(value="INVOKE",target="Lorg/mtr/mod/render/RenderRails;render()V",shift=At.Shift.AFTER),remap=false)
    private static void mtrengine$railAfter(CallbackInfo ci) {
        if(MtrRailModelBuilds.ENABLED)RailSectorBatcher.flush();
    }

    @Inject(method="render(Lorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V",
            at=@At("RETURN"), remap=false)
    private static void mtrengine$endPass(CallbackInfo ci){
        MtrRenderBridge.onFrameEnd();
    }
}
