package org.mtr.mod.mixin;
import org.mtr.mapping.render.obj.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="org.mtr.mapping.mapper.OptimizedModel",remap=false)
public interface ModelAtlasAccessor {
    @Accessor("ATLAS_MANAGER") static AtlasManager mtr$atlas() { throw new AssertionError(); }
}
