package org.mtr.mod.mixin;

import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.vertex.CapturingVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "org.mtr.mapping.mapper.OptimizedModel$MaterialGroup", remap = false)
public interface OptimizedMaterialGroupAccessor {
    @Accessor("materialProperties") MaterialProperties mtr$getMaterial();
    @Accessor("modelPartConsumers") List<Consumer<CapturingVertexConsumer>> mtr$getConsumers();
}
