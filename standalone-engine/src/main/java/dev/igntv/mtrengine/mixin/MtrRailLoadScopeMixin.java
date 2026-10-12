package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.renderer.MtrRailLoadScope;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.render.DynamicVehicleModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Concrete class hooks avoid unsupported injectors on the MTR interface. */
@Mixin(value=RailResource.class,remap=false)
public abstract class MtrRailLoadScopeMixin {
    @Inject(method="getOptimizedModel",at=@At("HEAD"),remap=false)
    private void mtrengine$startOptimized(CallbackInfoReturnable<OptimizedModelWrapper> ci){MtrRailLoadScope.enter();}
    @Inject(method="getOptimizedModel",at=@At("RETURN"),remap=false)
    private void mtrengine$endOptimized(CallbackInfoReturnable<OptimizedModelWrapper> ci){MtrRailLoadScope.exit();}

    @Inject(method="getDynamicVehicleModel",at=@At("HEAD"),remap=false)
    private void mtrengine$startDynamic(CallbackInfoReturnable<DynamicVehicleModel> ci){MtrRailLoadScope.enter();}
    @Inject(method="getDynamicVehicleModel",at=@At("RETURN"),remap=false)
    private void mtrengine$endDynamic(CallbackInfoReturnable<DynamicVehicleModel> ci){MtrRailLoadScope.exit();}

    @Inject(method="preload",at=@At("HEAD"),remap=false)
    private void mtrengine$startPreload(CallbackInfo ci){MtrRailLoadScope.enter();}
    @Inject(method="preload",at=@At("RETURN"),remap=false)
    private void mtrengine$endPreload(CallbackInfo ci){MtrRailLoadScope.exit();}
}
