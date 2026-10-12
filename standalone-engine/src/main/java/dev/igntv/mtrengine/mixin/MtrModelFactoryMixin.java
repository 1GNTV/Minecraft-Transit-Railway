package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.renderer.MtrRailLoadScope;
import dev.igntv.mtrengine.renderer.MtrRailModelBuilds;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optimizes stock MTR static model factories only while a rail resource
 * is being loaded. All other models (vehicles/signs/objects) remain stock.
 */
@Mixin(value=OptimizedModelWrapper.class,remap=false)
public abstract class MtrModelFactoryMixin {
    @Inject(method="fromMaterialGroups",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mtrengine$railBlockbench(
        ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper> groups,
        CallbackInfoReturnable<OptimizedModelWrapper> ci) {
        if(!MtrRailLoadScope.beginBuild())return;
        try{ci.setReturnValue(MtrRailModelBuilds.blockbench(groups));}
        finally{MtrRailLoadScope.endBuild();}
    }
    @Inject(method="fromObjModels",at=@At("HEAD"),cancellable=true,remap=false)
    private static void mtrengine$railObj(
        ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> models,
        CallbackInfoReturnable<OptimizedModelWrapper> ci) {
        if(!MtrRailLoadScope.beginBuild())return;
        try{ci.setReturnValue(MtrRailModelBuilds.obj(models));}
        finally{MtrRailLoadScope.endBuild();}
    }
}
