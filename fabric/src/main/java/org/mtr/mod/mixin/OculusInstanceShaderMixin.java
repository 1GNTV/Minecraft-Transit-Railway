package org.mtr.mod.mixin;
import org.mtr.mod.render.OculusInstanceRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Optional Oculus 1.8 integration: additional shader stages retain the unmodified program. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.programs.ShaderCreator",remap=false)
public abstract class OculusInstanceShaderMixin {
    @ModifyArgs(method={"create","createFallback"},at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/programs/ShaderCreator$IrisProgramResourceFactory;<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"),require=0,remap=false)
    private static void mtr$instanceSources(Args args){
        OculusInstanceRenderer.invalidatePrograms();
        if(args.get(2)!=null || args.get(3)!=null || args.get(4)!=null)return;
        var pair=OculusInstanceRenderer.prepare(args.get(1),args.get(5));
        if(pair!=null){args.set(1,pair.vertex());args.set(5,pair.fragment());}
    }
}
