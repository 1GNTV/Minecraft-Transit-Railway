package org.mtr.mod.mixin;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.mtr.mod.block.BlockStationDecor;
import org.mtr.mod.block.DecorCollisionIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockEntity.class)
public abstract class DecorLifecycleMixin {
    // Forge production uses SRG names; the QA/development runtime uses Mojmap.
    @Inject(method={"setLevel","m_142339_","clearRemoved","m_6339_","setBlockState","m_155250_"},at=@At("RETURN"),remap=false)
    private void sillon$load(CallbackInfo ci){if((Object)this instanceof BlockStationDecor.BlockEntity decor)DecorCollisionIndex.update(decor);}
    @Inject(method={"setRemoved","m_7651_"},at=@At("HEAD"),remap=false)
    private void sillon$remove(CallbackInfo ci){if((Object)this instanceof BlockStationDecor.BlockEntity decor)DecorCollisionIndex.remove(decor);}
}
