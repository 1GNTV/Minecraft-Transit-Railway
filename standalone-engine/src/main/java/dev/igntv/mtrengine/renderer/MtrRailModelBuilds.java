package dev.igntv.mtrengine.renderer;

import dev.igntv.mtrengine.mixin.MtrMaterialWrapperAccessor;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.resource.OptimizedModelWrapper;
import java.util.ArrayList;

/** Convert upstream rail geometry to retained GPU snapshots without
 * modifying official MTR's classes or resource pack formats. */
public final class MtrRailModelBuilds {
    // Disabled until startup, shader passes and visual geometry are verified.
    public static final boolean ENABLED=Boolean.getBoolean("mtr.engine.addon.regions");
    private MtrRailModelBuilds() {}
    public static OptimizedModelWrapper blockbench(
        ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper> groups) {
        if(!ENABLED || !OptimizedRenderer.hasOptimizedRendering() || groups==null) {
            return OptimizedModelWrapper.fromMaterialGroups(groups);
        }
        try {
            var materialGroups=new ArrayList<OptimizedModel.MaterialGroup>();
            for(var group:groups) {
                if(!((Object)group instanceof MtrMaterialWrapperAccessor accessor))
                    return OptimizedModelWrapper.fromMaterialGroups(groups);
                var material=accessor.mtrengine$getMaterialGroup();
                if(material!=null)materialGroups.add(material);
            }
            if(!ModelMeshCompiler.accessorsAvailable(materialGroups,java.util.List.of()))
                return OptimizedModelWrapper.fromMaterialGroups(groups);
            return MtrModelBridge.wrap(ModelMeshCompiler.compile(materialGroups,java.util.List.of(),true));
        } catch(RuntimeException unsupported) {
            dev.igntv.mtrengine.MtrEngineForge.LOGGER.warn("Rail Blockbench optimization failed; preserving upstream renderer",unsupported);
            return OptimizedModelWrapper.fromMaterialGroups(groups);
        }
    }
    public static OptimizedModelWrapper obj(
        ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> objects) {
        if(!ENABLED || !OptimizedRenderer.hasOptimizedRendering() || objects==null) {
            return OptimizedModelWrapper.fromObjModels(objects);
        }
        try {
            var raw=new ArrayList<OptimizedModel.ObjModel>();
            for(var wrapper:objects)raw.add(wrapper.objModel);
            if(!ModelMeshCompiler.accessorsAvailable(java.util.List.of(),raw))
                return OptimizedModelWrapper.fromObjModels(objects);
            return MtrModelBridge.wrap(ModelMeshCompiler.compile(java.util.List.of(),raw,true));
        } catch(RuntimeException unsupported) {
            dev.igntv.mtrengine.MtrEngineForge.LOGGER.warn("Rail OBJ optimization failed; preserving upstream renderer",unsupported);
            return OptimizedModelWrapper.fromObjModels(objects);
        }
    }
}
