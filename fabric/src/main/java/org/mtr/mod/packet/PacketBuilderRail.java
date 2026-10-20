package org.mtr.mod.packet;
import org.mtr.core.serializer.*;
import org.mtr.core.tool.Utilities;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mod.builder.BuilderRailRequest;
import java.util.*;
public final class PacketBuilderRail extends PacketRequestResponseBase {
    private final String raw;
    public record Receipt(String message,boolean success){}
    private static final Map<String,Receipt> RECEIPTS=new LinkedHashMap<>();
    public static Receipt take(String token){return RECEIPTS.remove(token);}
    public PacketBuilderRail(BuilderRailRequest request){this(Utilities.getJsonObjectFromData(request).toString());}
    private PacketBuilderRail(String raw){super(raw);this.raw=raw;}
    public PacketBuilderRail(PacketBufferReceiver p){this(p.readString());}
    @Override protected void runServerOutbound(ServerWorld world,ServerPlayerEntity sender){if(sender!=null&&sender.data.hasPermissions(2)&&raw.length()<16000)super.runServerOutbound(world,sender);}
    @Override protected void runClientInbound(JsonReader r){final String[] t={""},m={""},u={""};r.unpackString("token",v->t[0]=v);r.unpackString("message",v->m[0]=v);r.unpackString("update",v->u[0]=v);if(!u[0].isEmpty())PacketUpdateData.update(new JsonReader(Utilities.parseJson(u[0])));if(RECEIPTS.size()>64)RECEIPTS.clear();RECEIPTS.put(t[0],new Receipt(m[0],!u[0].isEmpty()));}
    @Override protected PacketRequestResponseBase getInstance(String raw){return new PacketBuilderRail(raw);}
    @Override protected SerializedDataBase getDataInstance(JsonReader r){return new BuilderRailRequest(r);}
    @Override protected String getKey(){return BuilderRailRequest.KEY;}
    @Override protected ResponseType responseType(){return ResponseType.ALL;}
}
