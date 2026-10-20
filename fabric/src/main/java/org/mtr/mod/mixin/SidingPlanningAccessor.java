package org.mtr.mod.mixin;
import org.mtr.core.data.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Siding.class,remap=false)
public interface SidingPlanningAccessor {
    @Accessor("pathSidingToMainRoute") ObjectArrayList<PathData> sillon$approach();
    @Accessor("pathMainRoute") ObjectArrayList<PathData> sillon$main();
    @Accessor("pathMainRouteToSiding") ObjectArrayList<PathData> sillon$return();
    @Accessor("defaultPathData") PathData sillon$defaultPath();
}
