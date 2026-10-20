package org.mtr.mod.packet;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.*;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.*;
import org.mtr.mod.decor.*;
import java.util.*;

/** Authoritative, bounded and preflighted edits. Repetition never replaces an occupied block. */
public final class PacketStationDecor extends PacketHandler {
    private final BlockPos source,first,last;
    private final int action,mask,count,axis,step;
    private final String value;
    private record Change(BlockPos pos,BlockState before,DecorSpec oldSpec,BlockState after,DecorSpec newSpec) {}
    private record Batch(Object world,long time,List<Change> changes) {}
    private static final Map<UUID,Deque<Batch>> HISTORY=new LinkedHashMap<>();
    public PacketStationDecor(BlockPos source,int action,String value,BlockPos first,BlockPos last,int mask,int count,int axis,int step){this.source=source;this.action=action;this.value=value;this.first=first;this.last=last;this.mask=mask;this.count=count;this.axis=axis;this.step=step;}
    public PacketStationDecor(PacketBufferReceiver p){source=BlockPos.fromLong(p.readLong());action=p.readInt();value=p.readString();first=BlockPos.fromLong(p.readLong());last=BlockPos.fromLong(p.readLong());mask=p.readInt();count=p.readInt();axis=p.readInt();step=p.readInt();}
    @Override public void write(PacketBufferSender p){p.writeLong(source.asLong());p.writeInt(action);p.writeString(value);p.writeLong(first.asLong());p.writeLong(last.asLong());p.writeInt(mask);p.writeInt(count);p.writeInt(axis);p.writeInt(step);}
    private static boolean near(BlockPos a,BlockPos b,int distance){return Math.abs((long)a.getX()-b.getX())<=distance&&Math.abs((long)a.getY()-b.getY())<=distance&&Math.abs((long)a.getZ()-b.getZ())<=distance;}
    private void reply(ServerPlayerEntity p,String message){Init.REGISTRY.sendPacketToClient(p,new PacketStationDecor(source,100,message,first,last,0,0,0,0));}
    @Override public void runClient(){if(action==100)org.mtr.mod.client.DecorBrushClient.feedback=value;}
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity sender){
        PlayerEntity player=new PlayerEntity(sender.data);World world=sender.getEntityWorld();
        if(!sender.data.hasPermissions(2)||!player.isHolding(Items.BRUSH.get().asItem())||!near(sender.getBlockPos(),source,8)||!Init.isChunkLoaded(world,source)){reply(sender,"Modification refusée : opérateur, brush et proximité requis.");return;}
        long now=System.currentTimeMillis();HISTORY.values().forEach(q->q.removeIf(b->now-b.time>600000));HISTORY.entrySet().removeIf(e->e.getValue().isEmpty());
        if(action==3){undo(world,sender);return;}
        var entity=world.getBlockEntity(source);if(entity==null||!(entity.data instanceof BlockStationDecor.BlockEntity decor))return;
        DecorSpec requested=DecorSpec.decode(value);if(requested.kind()!=decor.spec().kind()){reply(sender,"La famille de l’objet ne peut pas être remplacée.");return;}
        var changes=new ArrayList<Change>();BlockState sourceState=world.getBlockState(source);
        if(action==0)changes.add(new Change(source,sourceState,decor.spec(),sourceState,requested));
        else if(action==2){
            for(var o:DecorPlacement.repeat(count,axis,step)){BlockPos p=new BlockPos(source.getX()+o.x(),source.getY()+o.y(),source.getZ()+o.z());
                if(!Init.isChunkLoaded(world,p)||!world.getBlockState(p).isAir()){reply(sender,"Pose annulée : un emplacement est occupé ou non chargé.");return;}
                changes.add(new Change(p,world.getBlockState(p),null,sourceState,requested));}
        }else if(action==1||action==4){
            int x=Math.min(first.getX(),last.getX()),y=Math.min(first.getY(),last.getY()),z=Math.min(first.getZ(),last.getZ());
            int dx=Math.abs(first.getX()-last.getX()),dy=Math.abs(first.getY()-last.getY()),dz=Math.abs(first.getZ()-last.getZ());
            if(!near(source,first,48)||!near(source,last,48)||!DecorPlacement.validBox(dx,dy,dz)){reply(sender,"Zone trop grande : 48 blocs par axe et 32 768 blocs maximum.");return;}
            for(int i=0;i<=dx;i++)for(int j=0;j<=dy;j++)for(int k=0;k<=dz;k++){
                BlockPos p=new BlockPos(x+i,y+j,z+k);if(!Init.isChunkLoaded(world,p)){reply(sender,"Zone non chargée : aucune modification.");return;}
                var be=world.getBlockEntity(p);if(be==null||!(be.data instanceof BlockStationDecor.BlockEntity target)||target.spec().kind()!=requested.kind())continue;
                DecorSpec updated=target.spec().patch(requested,mask);
                if(action==4){DecorSpec t=target.spec();float ox=axis==0?source.getX()+requested.x()-p.getX():t.x(),oy=axis==1?source.getY()+requested.y()-p.getY():t.y(),oz=axis==2?source.getZ()+requested.z()-p.getZ():t.z();
                    if(Math.abs(ox)>8||Math.abs(oy)>8||Math.abs(oz)>8){reply(sender,"Alignement limité à 8 blocs de déplacement par objet.");return;}
                    updated=new DecorSpec(t.kind(),t.width(),t.height(),t.depth(),t.spacing(),t.suspension(),t.yaw(),t.pitch(),ox,oy,oz,t.color(),t.accent(),t.material(),t.variant(),t.illuminated(),t.image(),t.label());}
                changes.add(new Change(p,world.getBlockState(p),target.spec(),world.getBlockState(p),updated));
                if(changes.size()>256){reply(sender,"Plus de 256 objets : réduis la sélection.");return;}
            }
        }else return;
        if(changes.isEmpty()){reply(sender,"Aucun objet de cette famille dans la sélection.");return;}
        for(Change change:changes){if(change.oldSpec==null)world.setBlockState(change.pos,change.after);var be=world.getBlockEntity(change.pos);if(be!=null&&be.data instanceof BlockStationDecor.BlockEntity target)target.setSpec(change.newSpec);}
        if(HISTORY.size()>=64&&!HISTORY.containsKey(sender.getUuid()))HISTORY.remove(HISTORY.keySet().iterator().next());
        var history=HISTORY.computeIfAbsent(sender.getUuid(),id->new ArrayDeque<>());if(history.size()>=12)history.removeFirst();history.addLast(new Batch(world.data,now,List.copyOf(changes)));
        reply(sender,changes.size()+" objet(s) modifié(s). Annulation disponible pendant 10 min.");
    }
    private void undo(World world,ServerPlayerEntity sender){
        var h=HISTORY.get(sender.getUuid());if(h==null||h.isEmpty()){reply(sender,"Aucune opération à annuler.");return;}Batch batch=h.peekLast();
        if(batch.world!=world.data){reply(sender,"Retourne dans la dimension de l’opération.");return;}
        for(Change c:batch.changes){if(!near(sender.getBlockPos(),c.pos,56)||!Init.isChunkLoaded(world,c.pos)){reply(sender,"Approche-toi de la zone à restaurer.");return;}var be=world.getBlockEntity(c.pos);if(be==null||!(be.data instanceof BlockStationDecor.BlockEntity d)||!d.spec().equals(c.newSpec)){reply(sender,"Annulation refusée : un objet a été modifié depuis.");return;}}
        for(Change c:batch.changes){world.setBlockState(c.pos,c.before);if(c.oldSpec!=null){var be=world.getBlockEntity(c.pos);if(be!=null&&be.data instanceof BlockStationDecor.BlockEntity d)d.setSpec(c.oldSpec);}}
        h.removeLast();reply(sender,"Opération annulée ("+batch.changes.size()+" objets).");
    }
}
