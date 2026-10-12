package dev.igntv.mtrengine.mixin;

import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "org.mtr.mapping.mapper.OptimizedModel", remap = false)
public interface OptimizedModelLayoutAccessor {
    @Accessor("DEFAULT_MAPPING")
    static VertexAttributeMapping mtr$getDefaultMapping() { throw new UnsupportedOperationException("Mixin accessor not applied"); }
}
