package org.mtr.mod.mixin;
import org.mtr.core.generated.data.AreaBaseSchema;
import org.mtr.core.serializer.*;
import org.mtr.mod.builder.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=AreaBaseSchema.class,remap=false)
public abstract class AreaShapeSchemaMixin implements AreaShape {
    @Unique private RegionPolygon sillon$polygon;
    public RegionPolygon sillon$getPolygon(){return sillon$polygon;}
    public void sillon$setPolygon(RegionPolygon polygon){sillon$polygon=polygon;}
    @Inject(method="serializeData",at=@At("RETURN"))
    private void sillon$write(WriterBase writer,CallbackInfo ci){writer.writeString("sillonPolygon",sillon$polygon==null?"":sillon$polygon.encode());}
    @Inject(method="updateData",at=@At("RETURN"))
    private void sillon$read(ReaderBase reader,CallbackInfo ci){reader.unpackString("sillonPolygon",value->sillon$polygon=RegionPolygon.decode(value));}
}
