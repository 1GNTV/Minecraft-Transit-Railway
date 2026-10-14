package org.mtr.mod.packet;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.mapper.MinecraftServerHelper;
import org.mtr.mapping.mapper.PersistenceStateExtension;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;
import org.mtr.mod.data.PanamNewsClient;
import org.mtr.mod.data.PersistentStateData;

/** Server-to-client snapshot. Never accepts client-side writes. */
public final class PacketPanamNewsSync extends PacketHandler {
    private final String global;
    private final long[] ids;
    private final String[] notices;

    public PacketPanamNewsSync(PacketBufferReceiver receiver) {
        global = receiver.readString();
        int size = receiver.readInt();
        if (size < 0 || size > 128) throw new IllegalArgumentException("Invalid PANAM bulletin count");
        ids = new long[size];
        notices = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = receiver.readLong();
            notices[i] = receiver.readString();
        }
    }

    public PacketPanamNewsSync(PersistentStateData data) {
        global = data.getPanamGlobal();
        ids = data.getPanamRouteIds();
        notices = new String[ids.length];
        for (int i = 0; i < ids.length; i++) notices[i] = data.getPanamRoute(ids[i]);
    }

    @Override
    public void write(PacketBufferSender sender) {
        sender.writeString(global);
        sender.writeInt(ids.length);
        for (int i = 0; i < ids.length; i++) {
            sender.writeLong(ids[i]);
            sender.writeString(notices[i]);
        }
    }

    @Override
    public void runClient() {
        PanamNewsClient.update(global, ids, notices);
    }

    private static PersistentStateData getState(MinecraftServer server) {
        return (PersistentStateData) PersistenceStateExtension.register(
                server.getOverworld(), PersistentStateData::new, Init.MOD_ID);
    }

    public static void send(ServerPlayerEntity player) {
        Init.REGISTRY.sendPacketToClient(player, new PacketPanamNewsSync(getState(player.getEntityWorld().getServer())));
    }

    public static void broadcast(MinecraftServer server) {
        PacketPanamNewsSync packet = new PacketPanamNewsSync(getState(server));
        MinecraftServerHelper.iterateWorlds(server,
                world -> MinecraftServerHelper.iteratePlayers(world,
                        player -> Init.REGISTRY.sendPacketToClient(player, packet)));
    }
}
