package org.sillon.qa;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.data.SillonSignagePresets;
import java.nio.file.*;

/** Dedicated-server fixture preparation; no Gradle console forwarding is required. */
public final class SignageWorldQa {
    private static MinecraftServer server;
    private static int ticks;
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SignageWorldQa::started);
        MinecraftForge.EVENT_BUS.addListener(SignageWorldQa::tick);
    }
    private static void started(ServerStartedEvent event) {
        if(!event.getServer().isDedicatedServer())return;
        try {
            server=event.getServer();
            for(String command:Files.readAllLines(Path.of("signage-world-commands.txt")))
                if(!command.isBlank())server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command);
            for(int i=0;i<SillonSignagePresets.ALL.size();i++) {
                var entity=server.overworld().getBlockEntity(new BlockPos((i%5-2)*7,5+(i/5)*4,0));
                if(!(entity instanceof BlockSillonSignage.BlockEntity sign)
                    ||!sign.document().equals(SillonSignagePresets.ALL.get(i).document()))
                    throw new IllegalStateException("Dedicated server fixture mismatch "+i);
            }
            System.out.println("SIGNAGE_QA_FIXTURES_PLACED 23 native block entities verified");
            // After checking every untouched template, place the photographed manual fixtures for visual inspection.
            for(String command:Files.readAllLines(Path.of("signage-reference-commands.txt")))
                if(!command.isBlank()){
                    // /setblock may reject an unchanged block state before loading its replacement NBT.
                    String[] parts=command.split(" ",5);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"setblock "+parts[1]+" "+parts[2]+" "+parts[3]+" minecraft:air");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command);
                }
            var review=server.overworld().getBlockEntity(new BlockPos(0,5,0));
            if(!(review instanceof BlockSillonSignage.BlockEntity sign)||sign.document().stationAuto()||!sign.document().title().equals("Saint-Denis|Porte de Paris"))throw new IllegalStateException("Reference scene NBT was not applied");
            var diagram=server.overworld().getBlockEntity(new BlockPos(-7,17,0));
            if(!(diagram instanceof BlockSillonSignage.BlockEntity route)||route.document().rows().size()!=21)throw new IllegalStateException("Reference route stops were not persisted");
        }catch(Exception e){e.printStackTrace();System.exit(2);}
    }
    private static void tick(TickEvent.ServerTickEvent event) {
        if(server==null||event.phase!=TickEvent.Phase.END||++ticks<20)return;
        try {
            Files.writeString(Path.of("signage-world-qa-success.txt"),"23 real block entities verified on the dedicated Forge server.");
            System.out.println("SIGNAGE_QA_DEDICATED_SUCCESS");
            server.halt(false);server=null;
        }catch(Exception e){e.printStackTrace();System.exit(2);}
    }
}
