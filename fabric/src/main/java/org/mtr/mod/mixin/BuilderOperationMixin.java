package org.mtr.mod.mixin;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.mod.builder.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=OperationProcessor.class,remap=false)
public abstract class BuilderOperationMixin {
    @Inject(method="process",at=@At("HEAD"),cancellable=true)
    private static void sillon$plan(String key,SerializedDataBase data,Simulator simulator,CallbackInfoReturnable<SerializedDataBase> ci){if(BuilderJourneyRequest.KEY.equals(key)&&data instanceof BuilderJourneyRequest request)ci.setReturnValue(request.search(simulator));else if(BuilderPlanRequest.KEY.equals(key)&&data instanceof BuilderPlanRequest request)ci.setReturnValue(request.collect(simulator));else if(BuilderRailRequest.KEY.equals(key)&&data instanceof BuilderRailRequest request)ci.setReturnValue(request.apply(simulator));}
}
