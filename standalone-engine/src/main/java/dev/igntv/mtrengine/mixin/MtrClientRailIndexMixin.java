package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.renderer.MtrRailIndexes;
import org.mtr.mod.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Upstream 4.0.5 sync lifecycle, avoiding a fork-only field on ClientData. */
@Mixin(value=MinecraftClientData.class,remap=false)
public abstract class MtrClientRailIndexMixin {
    @Inject(method="sync()V",at=@At("RETURN"),remap=false,require=1)
    private void mtrengine$reindex(CallbackInfo ci){
        MtrRailIndexes.rebuild((MinecraftClientData)(Object)this);
    }
}
