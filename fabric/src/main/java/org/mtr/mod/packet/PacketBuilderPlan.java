package org.mtr.mod.packet;
import org.mtr.core.serializer.*;
import org.mtr.core.tool.Utilities;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mod.builder.BuilderPlanRequest;
import org.mtr.mod.Items;
import java.util.*;
public final class PacketBuilderPlan extends PacketRequestResponseBase {
    private final String raw;private static final Map<String,Long> REQUESTED=new LinkedHashMap<>();
    public PacketBuilderPlan(double x,double y,double z){this(Utilities.getJsonObjectFromData(new BuilderPlanRequest(x,y,z)).toString());}
    public static PacketBuilderPlan diagnostics(double x,double y,double z){var r=new BuilderPlanRequest(x,y,z);r.mode="diagnostics";return new PacketBuilderPlan(Utilities.getJsonObjectFromData(r).toString());}
    private PacketBuilderPlan(String raw){super(raw);this.raw=raw;}
    public PacketBuilderPlan(PacketBufferReceiver p){this(p.readString());}
    @Override protected void runServerOutbound(ServerWorld world,ServerPlayerEntity sender){if(sender==null)return;long now=System.currentTimeMillis();var r=new BuilderPlanRequest(new JsonReader(Utilities.parseJson(raw)));String key=sender.getUuid()+r.mode;if(now-REQUESTED.getOrDefault(key,0L)<1500)return;if(!Double.isFinite(r.x+r.y+r.z)||Math.abs(r.x-sender.getBlockPos().getX())>4||Math.abs(r.y-sender.getBlockPos().getY())>4||Math.abs(r.z-sender.getBlockPos().getZ())>4||("diagnostics".equals(r.mode)?!sender.data.hasPermissions(2):!new PlayerEntity(sender.data).isHolding(Items.BRUSH.get().asItem())))return;if(REQUESTED.size()>128)REQUESTED.clear();REQUESTED.put(key,now);super.runServerOutbound(world,sender);}
    @Override protected void runClientInbound(JsonReader r){r.unpackString("plan",text->{if(text.startsWith("{"))org.mtr.mod.client.BuilderDiagnosticsClient.accept(text);else org.mtr.mod.client.DoorGuideClient.accept(text);});}
    @Override protected PacketRequestResponseBase getInstance(String raw){return new PacketBuilderPlan(raw);}
    @Override protected SerializedDataBase getDataInstance(JsonReader r){return new BuilderPlanRequest(r);}
    @Override protected String getKey(){return BuilderPlanRequest.KEY;}
    @Override protected ResponseType responseType(){return ResponseType.PLAYER;}
}
