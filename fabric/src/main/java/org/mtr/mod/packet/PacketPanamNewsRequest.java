package org.mtr.mod.packet;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;

/** Refresh the overworld announcement snapshot after every dimension/world swap. */
public final class PacketPanamNewsRequest extends PacketHandler {
    public PacketPanamNewsRequest(PacketBufferReceiver ignored) {}
    public PacketPanamNewsRequest() {}
    @Override public void write(PacketBufferSender sender) {}
    @Override public void runServer(MinecraftServer server, ServerPlayerEntity player) {
        PacketPanamNewsSync.send(player);
    }
}
