package dev.igntv.mtrengine.mixin;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=OptimizedModelWrapper.MaterialGroupWrapper.class,remap=false)
public interface MtrMaterialWrapperAccessor {
    @Accessor("materialGroup")
    OptimizedModel.MaterialGroup mtrengine$getMaterialGroup();
}
