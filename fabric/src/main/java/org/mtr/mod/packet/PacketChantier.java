package org.mtr.mod.packet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.*;
import org.mtr.mod.chantier.ChantierServer;
public final class PacketChantier extends PacketHandler {
    private final int action;private final String project,raw;
    public PacketChantier(int action,String project,String raw){this.action=action;this.project=project;this.raw=raw;}
    public PacketChantier(PacketBufferReceiver p){action=p.readInt();project=p.readString();raw=p.readString();}
    @Override public void write(PacketBufferSender p){p.writeInt(action);p.writeString(project);p.writeString(raw);}
    @Override public void runServer(MinecraftServer server,ServerPlayerEntity p){if(project.length()!=36||raw.length()>24000)return;ChantierServer.request(p.data,action,project,raw);}
    @Override public void runClient(){if(action==105)org.mtr.mod.client.ChantierClient.visiting=raw.equals("1");else if(action==100)org.mtr.mod.client.ChantierClient.receipt(project,raw);else if(action==101)PacketUpdateData.update(new org.mtr.core.serializer.JsonReader(org.mtr.core.tool.Utilities.parseJson(raw)));else if(action==102){var result=new org.mtr.core.operation.DeleteDataResponse(new org.mtr.core.serializer.JsonReader(org.mtr.core.tool.Utilities.parseJson(raw)));result.write(org.mtr.mod.client.MinecraftClientData.getInstance());result.write(org.mtr.mod.client.MinecraftClientData.getDashboardInstance());}}
}
