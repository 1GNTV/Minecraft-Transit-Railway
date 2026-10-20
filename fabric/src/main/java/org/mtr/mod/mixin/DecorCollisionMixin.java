package org.mtr.mod.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.mtr.mod.block.DecorCollisionIndex;
import org.spongepowered.asm.mixin.Mixin;

/** Level inherits this method from LevelAccessor. Override on the concrete world:
 * Forge's Mixin 0.8.5 cannot inject into a default interface method.
 * The native iterator is retained, including other block collision hooks. */
@Mixin(Level.class)
public abstract class DecorCollisionMixin implements LevelAccessor {
    @Override
    public Iterable<VoxelShape> getBlockCollisions(Entity entity,AABB bounds){
        return DecorCollisionIndex.append((Level)(Object)this,bounds,LevelAccessor.super.getBlockCollisions(entity,bounds));
    }
}
