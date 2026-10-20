package org.mtr.mod.mixin;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.mtr.mod.block.BlockStationDecor;
import org.mtr.mod.block.DecorCollisionIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockEntity.class)
public abstract class DecorLifecycleMixin {
    @Inject(method={"setLevel","clearRemoved","setBlockState"},at=@At("RETURN"))
    private void sillon$load(CallbackInfo ci){if((Object)this instanceof BlockStationDecor.BlockEntity decor)DecorCollisionIndex.update(decor);}
    @Inject(method="setRemoved",at=@At("HEAD"))
    private void sillon$remove(CallbackInfo ci){if((Object)this instanceof BlockStationDecor.BlockEntity decor)DecorCollisionIndex.remove(decor);}
}
