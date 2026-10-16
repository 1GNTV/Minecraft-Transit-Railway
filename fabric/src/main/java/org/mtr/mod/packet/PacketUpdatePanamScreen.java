package org.mtr.mod.packet;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockPANAMScreen;
import org.mtr.mod.data.PanamScreenSettings;

/** Only edits a nearby, loaded PANAM screen while holding the MTR brush. */
public final class PacketUpdatePanamScreen extends PacketHandler {
    private final BlockPos pos;
    private final PanamScreenSettings settings;
    public PacketUpdatePanamScreen(BlockPos pos,PanamScreenSettings settings){this.pos=pos;this.settings=settings;}
    public PacketUpdatePanamScreen(PacketBufferReceiver b){
        pos=BlockPos.fromLong(b.readLong());
        settings=new PanamScreenSettings(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readInt(),b.readInt(),b.readFloat());
    }
    @Override public void write(PacketBufferSender b){
        b.writeLong(pos.asLong());b.writeFloat(settings.x);b.writeFloat(settings.y);b.writeFloat(settings.z);
        b.writeFloat(settings.yaw);b.writeFloat(settings.pitch);b.writeFloat(settings.roll);
        b.writeFloat(settings.width);b.writeFloat(settings.height);b.writeInt(settings.resolutionWidth);b.writeInt(settings.resolutionHeight);b.writeFloat(settings.zoom);
    }
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity player){
        if(!Init.isChunkLoaded(player.getEntityWorld(),pos)||!new PlayerEntity(player.data).isHolding(Items.BRUSH.get().asItem()))return;
        BlockPos at=player.getBlockPos();
        double dx=(double)at.getX()-pos.getX(),dy=(double)at.getY()-pos.getY(),dz=(double)at.getZ()-pos.getZ();
        if(dx*dx+dy*dy+dz*dz>64)return;
        org.mtr.mapping.holder.BlockEntity entity=player.getEntityWorld().getBlockEntity(pos);
        if(entity!=null&&entity.data instanceof BlockPANAMScreen.BlockEntity)((BlockPANAMScreen.BlockEntity)entity.data).setSettings(settings);
    }
}
