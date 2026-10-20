package org.mtr.mod.mixin;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Position;
import org.mtr.mod.builder.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=AreaBase.class,remap=false)
public abstract class AreaShapeMixin {
    @Inject(method="inArea(Lorg/mtr/core/data/Position;D)Z",at=@At("HEAD"),cancellable=true)
    private void sillon$contains(Position pos,double margin,CallbackInfoReturnable<Boolean> ci){
        var self=(AreaBase<?,?>)(Object)this;var polygon=AreaShape.polygon(self);if(polygon==null)return;
        ci.setReturnValue(pos.getY()>=self.getMinY()-margin&&pos.getY()<=self.getMaxY()+margin&&polygon.contains(pos.getX(),pos.getZ(),margin));
    }
    @Inject(method="intersecting",at=@At("HEAD"),cancellable=true)
    private void sillon$intersects(AreaBase<?,?> other,CallbackInfoReturnable<Boolean> ci){
        var self=(AreaBase<?,?>)(Object)this;
        if(AreaShape.polygon(self)==null&&AreaShape.polygon(other)==null)return;
        if(!AreaBase.validCorners(self)||!AreaBase.validCorners(other)){ci.setReturnValue(false);return;}
        ci.setReturnValue(self.getMinY()<=other.getMaxY()&&self.getMaxY()>=other.getMinY()&&AreaShape.outline(self).intersects(AreaShape.outline(other)));
    }
    @Inject(method="getCenter",at=@At("RETURN"),cancellable=true)
    private void sillon$center(CallbackInfoReturnable<Position> ci){
        var polygon=AreaShape.polygon((AreaBase<?,?>)(Object)this);if(polygon==null)return;var p=polygon.interior();
        ci.setReturnValue(new Position((long)Math.floor(p.x()),ci.getReturnValue().getY(),(long)Math.floor(p.z())));
    }
}
