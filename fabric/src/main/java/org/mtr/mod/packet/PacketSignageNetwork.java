package org.mtr.mod.packet;
import org.mtr.core.serializer.*;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mod.client.SillonSignageNetwork;

/** Same read-only network snapshot used by the MTR dashboard, without opening the dashboard. */
public final class PacketSignageNetwork extends PacketRequestResponseBase {
    public PacketSignageNetwork(){super("{}");}
    public PacketSignageNetwork(PacketBufferReceiver p){super(p);}
    private PacketSignageNetwork(String text){super(text);}
    @Override protected void runClientInbound(JsonReader reader){SillonSignageNetwork.accept(reader);}
    @Override protected PacketRequestResponseBase getInstance(String text){return new PacketSignageNetwork(text);}
    @Override protected SerializedDataBase getDataInstance(JsonReader reader){return new SerializedDataBase(){
        @Override public void updateData(ReaderBase r){}
        @Override public void serializeData(WriterBase w){}
    };}
    @Override protected String getKey(){return OperationProcessor.LIST_DATA;}
    @Override protected ResponseType responseType(){return ResponseType.PLAYER;}
}
