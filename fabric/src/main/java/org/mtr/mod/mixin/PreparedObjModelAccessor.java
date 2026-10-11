package org.mtr.mod.mixin;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.obj.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Pseudo
@Mixin(targets="org.mtr.mapping.mapper.OptimizedModel$ObjModel",remap=false)
public interface PreparedObjModelAccessor {
    @Invoker("<init>") static OptimizedModel.ObjModel mtr$create(List<RawMesh> meshes, boolean flipV, float x0,float y0,float z0,float x1,float y1,float z1) { throw new AssertionError(); }
}
