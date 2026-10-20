package org.mtr.mod.packet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.*;
import org.mtr.mod.Init;
import org.mtr.mod.builder.BuilderJourneyRequest;
import java.util.*;
public final class PacketBuilderJourney extends PacketHandler {
    private final String token,result;private final long origin,destination;
    private static final Map<String,String> RESULTS=new LinkedHashMap<>();
    private static final Map<UUID,Long> PENDING=new LinkedHashMap<>();
    public PacketBuilderJourney(String token,long origin,long destination){this(token,origin,destination,"");}
    private PacketBuilderJourney(String token,long origin,long destination,String result){this.token=token;this.origin=origin;this.destination=destination;this.result=result;}
    public PacketBuilderJourney(PacketBufferReceiver p){token=p.readString();origin=p.readLong();destination=p.readLong();result=p.readString();}
    @Override public void write(PacketBufferSender p){p.writeString(token);p.writeLong(origin);p.writeLong(destination);p.writeString(result);}
    public static String take(String token){return RESULTS.remove(token);}
    @Override public void runClient(){if(RESULTS.size()>32)RESULTS.clear();if(!result.isEmpty())RESULTS.put(token,result);}
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity sender){
        if(!result.isEmpty()||token.length()>64)return;
        long now=System.currentTimeMillis();PENDING.entrySet().removeIf(e->now-e.getValue()>15000);
        if(PENDING.containsKey(sender.getUuid())){reply(sender,BuilderJourneyRequest.error("Un calcul est déjà en cours. Réessaie dans quelques secondes."));return;}
        if(PENDING.size()>=128){reply(sender,BuilderJourneyRequest.error("Calculateur occupé. Réessaie dans quelques secondes."));return;}
        UUID player=sender.getUuid();PENDING.put(player,now);
        Init.sendMessageC2S(BuilderJourneyRequest.KEY,server,sender.getEntityWorld(),new BuilderJourneyRequest(origin,destination,raw->server.execute(()->{
            PENDING.remove(player,now);if(sender.data.connection!=null)reply(sender,raw);
        })),null,null);
    }
    private void reply(ServerPlayerEntity sender,String raw){Init.REGISTRY.sendPacketToClient(sender,new PacketBuilderJourney(token,origin,destination,raw));}
}
