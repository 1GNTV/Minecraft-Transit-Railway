package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.MtrRenderBridge;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Demonstrates interception of MTR's rail renderer from a SECOND JAR.
 * This intentionally does not cancel rendering or change vertex data.
 */
@Mixin(value=RenderRails.class, remap=false)
public abstract class MtrRailsPassMixin {
    @Inject(method="render()V", at=@At("HEAD"), remap=false)
    private static void mtrengine$railBegin(CallbackInfo ci) {
        MtrRenderBridge.onRailStart();
    }

    @Inject(method="render()V", at=@At("RETURN"), remap=false)
    private static void mtrengine$railEnd(CallbackInfo ci) {
        MtrRenderBridge.onRailEnd();
    }
}
