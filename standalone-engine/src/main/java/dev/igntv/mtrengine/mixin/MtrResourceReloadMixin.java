package dev.igntv.mtrengine.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.igntv.mtrengine.renderer.MtrRailModelBuilds;
import dev.igntv.mtrengine.renderer.MtrRailIndexes;
import dev.igntv.mtrengine.renderer.RailSectorBatcher;
import org.mtr.mod.client.CustomResourceLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR resource packs can replace models and their GPU buffers at runtime.
 * Queue Hotpath V2 geometry snapshots must never survive a resource reload.
 */
@Mixin(value=CustomResourceLoader.class,remap=false)
public abstract class MtrResourceReloadMixin {
    @Inject(method="reload",at=@At("HEAD"),remap=false,require=1)
    private static void mtrengine$invalidate(CallbackInfo ci){
        if(!MtrRailModelBuilds.ENABLED)return;
        Runnable free=()->{
            RailSectorBatcher.reload();
            MtrRailIndexes.clear();
        };
        if(RenderSystem.isOnRenderThread())free.run();
        else RenderSystem.recordRenderCall(free::run);
    }
}
