package org.mtr.mod.mixin;

import java.util.Map;
import org.mtr.mod.render.OculusShaderMapBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Experimental Oculus 1.8.0 source-map hook.
 *
 * The previous @ModifyArgs injector crashed Forge with missing Args$1.
 * @ModifyVariable does not generate that Sponge synthetic argument class.
 * If Oculus does not expose the expected transformed Map, require=0 means
 * this mixin does not change the renderer and original shaders are kept.
 */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.programs.ShaderCreator",remap=false)
public abstract class OculusShaderMapMixin {
    @SuppressWarnings({"rawtypes","unchecked"})
    @ModifyVariable(method="create",at=@At("STORE"),ordinal=0,require=0,remap=false)
    private static Map mtr$patchTransformedSources(Map transformed){
        return (Map)OculusShaderMapBridge.patch(transformed);
    }
}
