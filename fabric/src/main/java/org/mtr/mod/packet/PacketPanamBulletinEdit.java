package org.mtr.mod.packet;

import java.util.ArrayList;
import java.util.List;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.mapper.PersistenceStateExtension;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;
import org.mtr.mod.data.PanamBulletins;
import org.mtr.mod.data.PersistentStateData;

/** Operator-only mutations. Never trust the client or allow arbitrary NBT writes. */
public final class PacketPanamBulletinEdit extends PacketHandler {
    private final long routeId;
    private final int action, index, type;
    private final String message;
    public PacketPanamBulletinEdit(PacketBufferReceiver buffer){
        routeId=buffer.readLong();action=buffer.readInt();index=buffer.readInt();type=buffer.readInt();message=buffer.readString();
    }
    public PacketPanamBulletinEdit(long routeId,int action,int index,int type,String message){
        this.routeId=routeId;this.action=action;this.index=index;this.type=type;this.message=message;
    }
    @Override public void write(PacketBufferSender buffer){
        buffer.writeLong(routeId);buffer.writeInt(action);buffer.writeInt(index);buffer.writeInt(type);buffer.writeString(message);
    }
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity player){
        if(player==null||!player.data.hasPermissions(2))return;
        if(routeId < -1 || action<0 || action>2 || type<0 || type>3 || index<0 || index>PanamBulletins.LIMIT || message.length()>512)return;
        PersistentStateData state=(PersistentStateData) PersistenceStateExtension.register(
                server.getOverworld(),PersistentStateData::new,Init.MOD_ID);
        String packed=routeId==-1?state.getPanamGlobal():state.getPanamRoute(routeId);
        List<PanamBulletins.Bulletin> list=new ArrayList<>(PanamBulletins.decode(packed));
        if(action==0){
            if(list.size()>=PanamBulletins.LIMIT || message.trim().isEmpty())return;
            list.add(new PanamBulletins.Bulletin(type,message));
        }else if(action==1){
            if(index>=list.size()||message.trim().isEmpty())return;
            list.set(index,new PanamBulletins.Bulletin(type,message));
        }else{
            if(index>=list.size())return;
            list.remove(index);
        }
        String encoded=PanamBulletins.encode(list);
        if(routeId==-1)state.setPanamGlobal(encoded);else state.setPanamRoute(routeId,encoded);
        PacketPanamNewsSync.broadcast(server);
    }
}
