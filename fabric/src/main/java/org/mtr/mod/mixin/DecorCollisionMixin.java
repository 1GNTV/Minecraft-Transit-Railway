package org.mtr.mod.mixin;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.mtr.mod.decor.DecorCollisionIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CollisionGetter.class)
public interface DecorCollisionMixin {
    @Inject(method="getBlockCollisions",at=@At("RETURN"),cancellable=true)
    private void sillon$collisions(Entity entity,AABB bounds,CallbackInfoReturnable<Iterable<VoxelShape>> ci){
        if((Object)this instanceof Level level)ci.setReturnValue(DecorCollisionIndex.append(level,bounds,ci.getReturnValue()));
    }
}
