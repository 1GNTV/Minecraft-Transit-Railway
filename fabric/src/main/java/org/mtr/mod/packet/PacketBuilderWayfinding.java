package org.mtr.mod.packet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.PersistenceStateExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.*;
import org.mtr.mod.*;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockStationDecor;
import org.mtr.mod.builder.WalkGraph;
import org.mtr.mod.data.PersistentStateData;
import org.mtr.mod.decor.DecorKind;
import java.util.*;

/** Walking nodes belong to the dimension save, with server-side permissions and per-node revisions. */
public final class PacketBuilderWayfinding extends PacketHandler {
    private final int action;private final long id,sequence;private final String raw;
    private record Selection(Object world,BlockPos pos,long time){}
    private static final Map<UUID,Selection> SELECTED=new LinkedHashMap<>();
    private static final Map<UUID,Long> LAST_READ=new LinkedHashMap<>();
    public PacketBuilderWayfinding(int action,long id,long sequence,String raw){this.action=action;this.id=id;this.sequence=sequence;this.raw=raw;}
    public PacketBuilderWayfinding(PacketBufferReceiver p){action=p.readInt();id=p.readLong();sequence=p.readLong();raw=p.readString();}
    @Override public void write(PacketBufferSender p){p.writeInt(action);p.writeLong(id);p.writeLong(sequence);p.writeString(raw);}
    @Override public void runClient(){if(action==100)org.mtr.mod.client.WayfindingClient.accept(sequence,raw);else if(action==101)org.mtr.mod.client.WayfindingClient.feedback=raw;else if(action==102||action==103)org.mtr.mod.client.WayfindingClient.receipt(sequence,action==102,raw);}
    private static PersistentStateData store(ServerPlayerEntity p){return (PersistentStateData)PersistenceStateExtension.register(ServerWorld.cast(p.getEntityWorld()),PersistentStateData::new,Init.MOD_ID);}
    private static boolean marker(World w,BlockPos p){var be=w.getBlockEntity(p);return be!=null&&be.data instanceof BlockStationDecor.BlockEntity d&&d.spec().kind()==DecorKind.WAYPOINT;}
    private static WalkGraph.Node node(BlockPos p){return new WalkGraph.Node(p.asLong(),p.getX()+.5,p.getY()+.1,p.getZ()+.5,0,"","passage",0,List.of());}
    private static void feedback(ServerPlayerEntity p,String text){Init.REGISTRY.sendPacketToClient(p,new PacketBuilderWayfinding(101,0,0,text));p.sendMessage(new Text(TextHelper.literal(text).data),true);}
    private static void snapshot(ServerPlayerEntity p,long sequence){
        var graph=store(p).walking();var pos=p.getBlockPos();int limit=128;String encoded;
        do{encoded=graph.near(pos.getX(),pos.getY(),pos.getZ(),limit).encode();limit-=8;}while(encoded.length()>26000&&limit>0);
        Init.REGISTRY.sendPacketToClient(p,new PacketBuilderWayfinding(100,0,sequence,Init.getWorldId(p.getEntityWorld())+"\0"+encoded));
    }
    public static void select(ServerPlayerEntity p,BlockPos pos){
        if(!p.data.hasPermissions(2)||!marker(p.getEntityWorld(),pos))return;
        long now=System.currentTimeMillis();SELECTED.entrySet().removeIf(e->now-e.getValue().time()>60000);
        var first=SELECTED.remove(p.getUuid());
        if(first==null||first.world()!=p.getEntityWorld().data){if(SELECTED.size()>128)SELECTED.clear();SELECTED.put(p.getUuid(),new Selection(p.getEntityWorld().data,pos,now));feedback(p,"Premier point choisi. Maj + clic sur le point suivant pour les relier.");return;}
        if(!Init.isChunkLoaded(p.getEntityWorld(),first.pos())||!marker(p.getEntityWorld(),first.pos())){feedback(p,"Le premier point n’existe plus.");return;}
        try{var save=store(p);var graph=save.walking();if(graph.get(first.pos().asLong())==null)graph.put(node(first.pos()));if(graph.get(pos.asLong())==null)graph.put(node(pos));graph.link(first.pos().asLong(),pos.asLong(),true);save.walkingChanged();feedback(p,"Cheminement relié dans les deux sens.");snapshot(p,0);}catch(RuntimeException e){feedback(p,e.getMessage());}
    }
    private void editReply(ServerPlayerEntity sender,boolean success,String message){Init.REGISTRY.sendPacketToClient(sender,new PacketBuilderWayfinding(success?102:103,id,sequence,message));}
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity sender){
        if(action==0){
            long now=System.currentTimeMillis(),last=LAST_READ.getOrDefault(sender.getUuid(),0L);if(now-last<500)return;
            if(LAST_READ.size()>128)LAST_READ.clear();LAST_READ.put(sender.getUuid(),now);snapshot(sender,sequence);return;
        }
        BlockPos pos=BlockPos.fromLong(id);
        if(!sender.data.hasPermissions(2)||!new PlayerEntity(sender.data).isHolding(Items.BRUSH.get().asItem())||Math.pow(sender.getBlockPos().getX()-pos.getX(),2)+Math.pow(sender.getBlockPos().getY()-pos.getY(),2)+Math.pow(sender.getBlockPos().getZ()-pos.getZ(),2)>64||!Init.isChunkLoaded(sender.getEntityWorld(),pos)||!marker(sender.getEntityWorld(),pos)||raw.length()>4096){editReply(sender,false,"Modification refusée : approche-toi du point avec le brush.");return;}
        try{
            var save=store(sender);var graph=save.walking();
            if(action==1){
                var edited=WalkGraph.decode(raw);if(edited.nodes().size()!=1)throw new IllegalArgumentException("Un seul point à modifier.");
                var n=edited.nodes().iterator().next();var old=graph.get(id);long revision=old==null?0:old.revision();
                if(n.id()!=id||n.revision()!=revision)throw new IllegalArgumentException("Point modifié ailleurs : rouvre son éditeur.");
                var retained=old==null?List.<Long>of():old.links().stream().filter(n.links()::contains).toList();if(old!=null)for(long other:old.links())if(!retained.contains(other)&&graph.get(other)!=null)graph.link(id,other,false);long updated=graph.get(id)==null?0:graph.get(id).revision();graph.put(new WalkGraph.Node(id,pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5,n.platform(),n.label(),n.kind(),updated+1,retained));
            }else if(action==2){long other=Long.parseLong(raw);graph.link(id,other,false);}
            else if(action==3)graph.remove(id);else return;
            save.walkingChanged();snapshot(sender,0);editReply(sender,true,"Cheminement enregistré.");
        }catch(RuntimeException e){editReply(sender,false,e.getMessage()==null?"Cheminement invalide.":e.getMessage());}
    }
}
