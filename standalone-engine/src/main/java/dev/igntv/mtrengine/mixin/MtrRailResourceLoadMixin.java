package dev.igntv.mtrengine.mixin;

import dev.igntv.mtrengine.renderer.MtrRailModelBuilds;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.resource.StoredModelResourceBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The 79-FPS fork modified StoredModelResourceBase.load() to retain
 * rail-only snapshots. On official MTR we intercept the TWO resource
 * factory calls, leaving signs/trains/decorations untouched.
 */
@Mixin(value=StoredModelResourceBase.class,remap=false)
public interface MtrRailResourceLoadMixin {
    @Redirect(method="load",at=@At(value="INVOKE",
        target="Lorg/mtr/mod/resource/OptimizedModelWrapper;fromMaterialGroups(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Lorg/mtr/mod/resource/OptimizedModelWrapper;"),
        require=1,remap=false)
    private OptimizedModelWrapper mtrengine$railGroups(ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper> groups) {
        if((Object)this instanceof RailResource) return MtrRailModelBuilds.blockbench(groups);
        return OptimizedModelWrapper.fromMaterialGroups(groups);
    }

    @Redirect(method="load",at=@At(value="INVOKE",
        target="Lorg/mtr/mod/resource/OptimizedModelWrapper;fromObjModels(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Lorg/mtr/mod/resource/OptimizedModelWrapper;"),
        require=1,remap=false)
    private OptimizedModelWrapper mtrengine$railObj(ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> objects) {
        if((Object)this instanceof RailResource) return MtrRailModelBuilds.obj(objects);
        return OptimizedModelWrapper.fromObjModels(objects);
    }
}
