package org.mtr.mod.mixin;
import org.mtr.mod.render.ModelDetailCache;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="org.mtr.mapping.render.model.RawMesh",remap=false)
public abstract class ModelDetailUploadMixin {
    @Inject(method="upload(Lorg/mtr/mapping/render/vertex/VertexAttributeMapping;)Lorg/mtr/mapping/render/model/Mesh;",at=@At("RETURN"))
    private void mtr$retainDetailGeometry(VertexAttributeMapping mapping,CallbackInfoReturnable<Mesh> cir){ModelDetailCache.uploaded(cir.getReturnValue(),(RawMesh)(Object)this);}
}
