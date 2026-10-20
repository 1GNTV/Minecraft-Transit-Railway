package org.mtr.mod.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.mtr.mod.block.DecorCollisionIndex;
import org.spongepowered.asm.mixin.Mixin;

/** Concrete implementation of the inherited query: interface injectors are unsupported
 * by Forge 1.20.1's Mixin. The helper retains Minecraft's BlockCollisions iterator. */
@Mixin(Level.class)
public abstract class DecorCollisionMixin implements LevelAccessor {
    @Override public Iterable<VoxelShape> getBlockCollisions(Entity entity,AABB bounds){
        return DecorCollisionIndex.collisions((Level)(Object)this,entity,bounds);
    }
}
