package dev.igntv.mtrengine.mixin;

import org.mtr.mapping.render.model.RawModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "org.mtr.mapping.mapper.OptimizedModel$ObjModel", remap = false)
public interface OptimizedObjModelAccessor {
	@Accessor("rawModel")
	RawModel mtr$getRawModel();
}
