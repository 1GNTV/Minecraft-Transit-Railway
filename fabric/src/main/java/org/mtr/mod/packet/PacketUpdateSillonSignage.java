package org.mtr.mod.packet;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.data.SillonSignageSpec;
import org.mtr.mod.data.SillonSignageOptions;
import org.mtr.mod.data.SillonSignageRow;

/** Server verifies chunk, brush and distance. All inputs are clamped by the document constructor. */
public final class PacketUpdateSillonSignage extends PacketHandler {
    private final BlockPos pos;
    private final SillonSignageSpec spec;
    public PacketUpdateSillonSignage(BlockPos pos,SillonSignageSpec spec){this.pos=pos;this.spec=spec;}
    public PacketUpdateSillonSignage(PacketBufferReceiver p){
        pos=BlockPos.fromLong(p.readLong());
        spec=new SillonSignageSpec(p.readString(),p.readString(),p.readString(),p.readString(),p.readString(),p.readString(),
            p.readInt(),p.readInt(),p.readInt(),p.readInt(),p.readFloat(),p.readFloat(),
            p.readFloat(),p.readFloat(),p.readFloat(),p.readBoolean(),p.readBoolean(),p.readBoolean(),
            SillonSignageOptions.decode(p.readString()),SillonSignageRow.decode(p.readString())).withBinding(org.mtr.mod.data.SillonSignageBinding.decode(p.readString()));
    }
    @Override public void write(PacketBufferSender p){
        p.writeLong(pos.asLong());
        p.writeString(spec.title());p.writeString(spec.subtitle());p.writeString(spec.body());
        p.writeString(spec.style());p.writeString(spec.icons());p.writeString(spec.mount());
        p.writeInt(spec.arrow());p.writeInt(spec.background());p.writeInt(spec.foreground());p.writeInt(spec.accent());
        p.writeFloat(spec.width());p.writeFloat(spec.height());p.writeFloat(spec.yaw());
        p.writeFloat(spec.pitch());p.writeFloat(spec.roll());
        p.writeBoolean(spec.stationAuto());p.writeBoolean(spec.illuminated());p.writeBoolean(spec.doubleSided());
        p.writeString(spec.options().encode());p.writeString(SillonSignageRow.encode(spec.rows()));p.writeString(spec.binding().encode());
    }
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity sender){
        World world=sender.getEntityWorld();
        if(!Init.isChunkLoaded(world,pos)||!new PlayerEntity(sender.data).isHolding(Items.BRUSH.get().asItem()))return;
        BlockPos at=sender.getBlockPos();
        double dx=(double)at.getX()-pos.getX(),dy=(double)at.getY()-pos.getY(),dz=(double)at.getZ()-pos.getZ();
        if(dx*dx+dy*dy+dz*dz>64)return;
        BlockEntity block=world.getBlockEntity(pos);
        if(block!=null&&block.data instanceof BlockSillonSignage.BlockEntity sign)
            sign.setDocument(spec);
    }
}
