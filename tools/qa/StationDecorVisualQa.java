package org.sillon.qa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.mtr.mod.decor.*;
import org.mtr.mod.block.BlockStationDecor;
import org.mtr.mod.screen.StationDecorScreen;
import org.mtr.mod.packet.PacketStationDecor;
import org.mtr.mod.InitClient;
import org.mtr.mapping.holder.BlockPos;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Checks the actual Forge deliverable, outside the production JAR. */
public final class StationDecorVisualQa {
    private static boolean active;
    private static int stage,ticks;
    private static final BlockPos TEST=new BlockPos(0,4,110);
    public static void register(){MinecraftForge.EVENT_BUS.addListener(StationDecorVisualQa::tick);}
    public static void begin(Minecraft mc){
        active=true;stage=-1;ticks=0;mc.setScreen(null);mc.options.hideGui=true;
        mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var level=server.overworld();
            for(DecorKind k:DecorKind.values()){
                int i=k.ordinal(),x=(i%4)*8-12,z=34+(i/4)*14;
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,4,z),org.mtr.mod.Blocks.STATION_DECOR.get(k).get().data.defaultBlockState());
                var be=level.getBlockEntity(new net.minecraft.core.BlockPos(x,4,z));
                if(!(be instanceof BlockStationDecor.BlockEntity entity)||entity.spec().kind()!=k)throw new IllegalStateException("Wrong decor family "+k);
                // A real floor behind each individual preview, without third-party content.
                for(int xx=x-5;xx<=x+5;xx++)for(int zz=z-4;zz<=z+4;zz++)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(xx,3,zz),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            level.setBlockAndUpdate(net.minecraft.core.BlockPos.of(TEST.asLong()),org.mtr.mod.Blocks.STATION_DECOR.get(DecorKind.BENCH).get().data.defaultBlockState());
            var player=server.getPlayerList().getPlayer(mc.player.getUUID());if(player!=null){player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.mtr.mod.Items.BRUSH.get().data));}
            mc.execute(()->{stage=0;ticks=0;camera(mc,0);});
        });
    }
    private static void camera(Minecraft mc,int index){
        DecorKind k=DecorKind.values()[index];int x=(index%4)*8-12,z=34+(index/4)*14;
        double distance=k==DecorKind.VAULT?8:4.5;
        mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());if(p!=null)p.teleportTo(mc.getSingleplayerServer().overworld(),x+.5,4+Math.max(.2,k.height*.45),z-distance,0,8);});
    }
    private static void setupUi(Minecraft mc,int n)throws Exception {
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),n<4?1120:640,n<4?720:480);mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.hideGui=false;
        StationDecorScreen screen=new StationDecorScreen(TEST,DecorSpec.defaults(DecorKind.BENCH));mc.setScreen(screen);
        Field tab=screen.getClass().getDeclaredField("tab");tab.setAccessible(true);tab.setInt(screen,n%4);Method arrange=screen.getClass().getDeclaredMethod("arrange");arrange.setAccessible(true);arrange.invoke(screen);
    }
    private static void checkUi(Minecraft mc)throws Exception {
        var screen=mc.screen;List<net.minecraft.client.gui.components.AbstractWidget> widgets=new ArrayList<>();
        for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible){
            if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height)throw new IllegalStateException("Decor widget outside viewport");
            for(var b:widgets)if(w.getX()<b.getX()+b.getWidth()&&w.getX()+w.getWidth()>b.getX()&&w.getY()<b.getY()+b.getHeight()&&w.getY()+w.getHeight()>b.getY())throw new IllegalStateException("Decor widgets overlap");widgets.add(w);
        }
    }
    private static void packet(Minecraft mc,int action){
        DecorSpec base=DecorSpec.defaults(DecorKind.BENCH);DecorSpec changed=new DecorSpec(base.kind(),base.width(),base.height(),base.depth(),base.spacing(),base.suspension(),0,0,0,0,0,0x275E91,0xFFFFFF,1,0,false,"","");
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketStationDecor(TEST,action,changed.encode(),TEST,TEST,2,2,0,3));
    }
    private static void tick(TickEvent.ClientTickEvent event){
        if(!active||event.phase!=TickEvent.Phase.END||stage<0)return;Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        try{
            ticks++;
            if(stage<20){
                if(ticks==70){String name=String.format(Boolean.getBoolean("sillon.qa.shader")?"decor-shader-%02d.png":"decor-world-%02d.png",stage);Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("DECOR_QA_CAPTURE "+message.getString()));}
                if(ticks>=85){stage++;ticks=0;if(stage<20)camera(mc,stage);else {mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());if(p!=null)p.teleportTo(mc.getSingleplayerServer().overworld(),.5,5,107,0,0);});setupUi(mc,0);}}
            }else if(stage<28){
                if(ticks==30){checkUi(mc);Screenshot.grab(mc.gameDirectory,String.format("decor-ui-%02d.png",stage-20),mc.getMainRenderTarget(),message->System.out.println("DECOR_QA_UI "+message.getString()));}
                if(ticks>=40){stage++;ticks=0;if(stage<28)setupUi(mc,stage-20);else {mc.setScreen(null);packet(mc,0);}}
            }else if(stage==28&&ticks==35){
                var be=mc.level.getBlockEntity(net.minecraft.core.BlockPos.of(TEST.asLong()));if(!(be instanceof BlockStationDecor.BlockEntity d)||d.spec().color()!=0x275E91)throw new IllegalStateException("Decor brush edit not synchronized");packet(mc,2);stage++;ticks=0;
            }else if(stage==29&&ticks==35){
                for(int x:new int[]{3,6}){var be=mc.level.getBlockEntity(new net.minecraft.core.BlockPos(x,4,110));if(!(be instanceof BlockStationDecor.BlockEntity d)||d.spec().color()!=0x275E91)throw new IllegalStateException("Brush repetition failed");}
                packet(mc,3);stage++;ticks=0;
            }else if(stage==30&&ticks==35){
                for(int x:new int[]{3,6})if(!mc.level.getBlockState(new net.minecraft.core.BlockPos(x,4,110)).isAir())throw new IllegalStateException("Undo did not restore air");
                String file=Boolean.getBoolean("sillon.qa.shader")?"decor-shader-qa-success.txt":"decor-native-qa-success.txt";
                Files.writeString(Path.of(file),"20 native object views; 8 editor views at 2 resolutions; real brush packet save, server/client sync, repeat and undo verified.");System.out.println("DECOR_QA_SUCCESS");active=false;mc.stop();
            }
        }catch(Throwable e){e.printStackTrace();System.exit(2);}
    }
}
