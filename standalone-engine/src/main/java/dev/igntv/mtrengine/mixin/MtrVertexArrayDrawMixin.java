package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.renderer.RegionVertexRanges;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import dev.igntv.mtrengine.renderer.GlVertexAttributeCache;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU draw-span isolation: inactive arrays still use their original GL call. */
@Pseudo
@Mixin(targets="org.mtr.mapping.render.object.VertexArray",remap=false)
public abstract class MtrVertexArrayDrawMixin {
    @Shadow @Final public VertexAttributeMapping mapping;
    @Redirect(method="draw",at=@At(value="INVOKE",
        target="Lorg/lwjgl/opengl/GL33;glDrawElements(IIIJ)V"),require=1,remap=false)
    private void mtrengine$drawRanges(int mode,int count,int type,long offset){
        if(dev.igntv.mtrengine.renderer.MtrRailModelBuilds.ENABLED)
            RegionVertexRanges.draw((VertexArray)(Object)this,mode,count,type,offset);
        else org.lwjgl.opengl.GL33.glDrawElements(mode,count,type,offset);
    }
    @Inject(method="draw",at=@At("RETURN"),require=1,remap=false)
    private void mtrengine$afterDraw(CallbackInfo ci){
        if(GlVertexAttributeCache.isActive()){
            mapping.sources.forEach((type,source)->{
                if(source==VertexAttributeSource.VERTEX_BUFFER || source==VertexAttributeSource.INSTANCE_BUFFER)
                    GlVertexAttributeCache.invalidate(type.location,type.span);
            });
        }
    }
    @Inject(method="close",at=@At("HEAD"),require=1,remap=false)
    private void mtrengine$cleanupRanges(CallbackInfo ci){
        RegionVertexRanges.remove((VertexArray)(Object)this);
    }
}
