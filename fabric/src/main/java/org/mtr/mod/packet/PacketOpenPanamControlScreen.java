package org.mtr.mod.packet;

import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;

public final class PacketOpenPanamControlScreen extends PacketHandler {
    public PacketOpenPanamControlScreen(PacketBufferReceiver buffer){}
    public PacketOpenPanamControlScreen(){}
    @Override public void write(PacketBufferSender buffer){}
    @Override public void runClient(){ClientPacketHelper.openPanamControlScreen();}
}
