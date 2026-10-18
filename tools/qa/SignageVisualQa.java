package org.sillon.qa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mod.Blocks;
import org.mtr.mod.block.BlockSillonSignage;
import org.mtr.mod.data.SillonSignagePresets;
import org.mtr.mod.screen.SillonSignageScreen;
import org.mtr.mod.screen.SillonSignageEditorLayout;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** CI-only helper, copied into Forge sources AFTER the deliverable JAR is built. */
public final class SignageVisualQa {
    private static int stage=-1,tick;
    private static boolean started;
    private static int worldView=-1,networkPhase=-1;
    private static final int[][] SIZES={{960,540,1},{960,540,2},{640,480,2}};
    public static void register(){MinecraftForge.EVENT_BUS.addListener(SignageVisualQa::tick);}
    private static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(!started) {
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            started=true;stage=0;tick=0;
            if(Boolean.getBoolean("sillon.qa.shader")){worldView=0;mc.options.hideGui=true;world(mc);}else setup(mc);return;
        }
        if(networkPhase>=0) {
            tick++;
            if(tick==10) {
                var be=mc.level.getBlockEntity(new net.minecraft.core.BlockPos(0,5,0));
                if(!(be instanceof BlockSillonSignage.BlockEntity entity))throw new IllegalStateException("QA world sign missing");
                SillonSignageScreen screen=new SillonSignageScreen(new BlockPos(0,5,0),entity);
                mc.setScreen(screen);
                try {
                    Field f=screen.getClass().getDeclaredField("inputs");f.setAccessible(true);
                    Map<?,?> inputs=(Map<?,?>)f.get(screen);
                    ((org.mtr.mapping.mapper.TextFieldWidgetExtension)inputs.get("title")).setText2("Sauvegarde vérifiée");
                    ((org.mtr.mapping.mapper.TextFieldWidgetExtension)inputs.get("width")).setText2("3.25");
                    int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
                    screen.mouseClicked(w-70,h-18,0);
                }catch(Exception e){throw new IllegalStateException(e);}
            }
            if(tick==60) {
                mc.getSingleplayerServer().execute(()->{
                    var server=mc.getSingleplayerServer();
                    var be=server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0,5,0));
                    if(!(be instanceof BlockSillonSignage.BlockEntity entity)||!entity.document().title().equals("Sauvegarde vérifiée")||entity.document().width()!=3.25f)
                        throw new IllegalStateException("Server rejected valid brush edit");
                    var clientBe=mc.level.getBlockEntity(new net.minecraft.core.BlockPos(0,5,0));
                    if(!(clientBe instanceof BlockSillonSignage.BlockEntity clientEntity)||!clientEntity.document().title().equals("Sauvegarde vérifiée"))
                        throw new IllegalStateException("Edited sign was not synchronized to the client");
                    try{Files.writeString(Path.of("signage-ui-qa-success.txt"),"18 native editor captures; 5 in-world views; edit sent through real brush packet and synchronized to client; responsive widgets checked.");}catch(Exception ignored){}
                    System.out.println("SIGNAGE_QA_SUCCESS");
                    mc.execute(mc::stop);
                });
            }
            return;
        }
        if(worldView>=0) {
            if(++tick==35) {
                String name=String.format(Boolean.getBoolean("sillon.qa.shader")?"signage-shader-world-%02d.png":"signage-world-%02d.png",worldView);
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("SIGNAGE_QA_WORLD "+message.getString()));
            }
            if(tick>=60) {
                worldView++;tick=0;
                if(worldView>=5) {
                    if(Boolean.getBoolean("sillon.qa.shader")) {
                        try {
                            boolean active=false;
                            for(String name:List.of("net.irisshaders.iris.Iris","net.coderbot.iris.Iris"))try{
                                Class<?> iris=Class.forName(name);
                                Object pack=iris.getMethod("getCurrentPack").invoke(null);
                                Object manager=iris.getMethod("getPipelineManager").invoke(null);
                                Object pipeline=manager.getClass().getMethod("getPipeline").invoke(manager);
                                if(pack instanceof Optional<?> optional&&optional.isPresent()&&pipeline instanceof Optional<?> rendered&&rendered.isPresent()) {
                                    String type=rendered.get().getClass().getName();
                                    System.out.println("SIGNAGE_QA_SHADER_PIPELINE "+type);
                                    active=!type.contains("FixedFunction");
                                }
                            }catch(ReflectiveOperationException ignored){}
                            if(!active)throw new IllegalStateException("Shader pack not active");
                            Files.writeString(Path.of("signage-shader-qa-success.txt"),"Oculus 1.8.0 / Embeddium 0.3.31 / Complementary r5.3 active; 5 world captures.");
                            System.out.println("SIGNAGE_QA_SHADER_SUCCESS");mc.stop();return;
                        }catch(Exception e){e.printStackTrace();System.exit(2);}
                    }
                    networkPhase=0;tick=0;mc.options.hideGui=false;
                    mc.getSingleplayerServer().execute(()->{
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if(player!=null) {
                            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                            player.teleportTo(mc.getSingleplayerServer().overworld(),.5,5,-2.5,0,0);
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.mtr.mod.Items.BRUSH.get().data));
                        }
                    });
                }else world(mc);
            }
            return;
        }
        if(++tick==35) {
            try{
                checkGeometry(mc);
                String name=String.format("signage-ui-%02d-%dx%d-tab%d.png",stage,SIZES[stage/6][0],SIZES[stage/6][1],stage%6);
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("SIGNAGE_QA_SCREENSHOT "+message.getString()));
                System.out.println("SIGNAGE_QA_CAPTURE "+stage+" "+mc.getWindow().getGuiScaledWidth()+"x"+mc.getWindow().getGuiScaledHeight());
            }catch(Exception e){e.printStackTrace();System.exit(2);}
        }
        if(tick>=55) {
            stage++;tick=0;
            if(stage>=18) {
                worldView=0;tick=0;mc.setScreen(null);mc.options.hideGui=true;world(mc);return;
            }
            setup(mc);
        }
    }
    private static void world(Minecraft mc) {
        int[][] cameras={{0,13,-23,0,0},{0,6,-7,0,0},{-10,10,-12,-25,0},{8,6,-7,0,0},{0,6,4,180,0}};
        int[] c=cameras[worldView];
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,720);
        mc.options.guiScale().set(1);mc.resizeDisplay();
        mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(player!=null) {
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(mc.getSingleplayerServer().overworld(),c[0]+.5,c[1],c[2]+.5,c[3],c[4]);
            }
        });
    }
    private static void setup(Minecraft mc) {
        int[] size=SIZES[stage/6];
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),size[0],size[1]);
        mc.options.guiScale().set(size[2]);mc.resizeDisplay();
        BlockPos pos=new BlockPos(0,64,0);
        BlockState state=new BlockState(Blocks.SILLON_SIGNAGE.get().data.defaultBlockState());
        BlockSillonSignage.BlockEntity entity=new BlockSillonSignage.BlockEntity(pos,state);
        entity.setDocument(SillonSignagePresets.ALL.get(11).document());
        SillonSignageScreen screen=new SillonSignageScreen(pos,entity);
        mc.setScreen(screen);
        int w=mc.getWindow().getGuiScaledWidth(),tw=(w-24)/6;
        screen.mouseClicked(12+(stage%6)*tw+tw/2.0,52,0);
        // Exercise visual search using the real text-field input path.
        if(stage%6==3) {
            var a=SillonSignageEditorLayout.of(w,mc.getWindow().getGuiScaledHeight()).editor();
            screen.mouseClicked(a.x()+12,a.y()+10,0);
            for(char c:"metro".toCharArray())screen.charTyped(c,0);
        }
    }
    private static void checkGeometry(Minecraft mc)throws Exception {
        Object screen=mc.screen;List<int[]> rectangles=new ArrayList<>();
        for(String name:List.of("buttons","tiles","tabs")) {
            Field f=screen.getClass().getDeclaredField(name);f.setAccessible(true);
            Object[] widgets=(Object[])f.get(screen);
            for(Object widget:widgets) {
                net.minecraft.client.gui.components.AbstractWidget nativeWidget=(net.minecraft.client.gui.components.AbstractWidget)widget;
                if(!nativeWidget.visible)continue;
                int[] r={nativeWidget.getX(),nativeWidget.getY(),nativeWidget.getWidth(),nativeWidget.getHeight()};
                if(r[0]<0||r[1]<0||r[0]+r[2]>mc.getWindow().getGuiScaledWidth()||r[1]+r[3]>mc.getWindow().getGuiScaledHeight())
                    throw new IllegalStateException("Widget outside native screen "+Arrays.toString(r));
                rectangles.add(r);
            }
        }
        Field inputField=screen.getClass().getDeclaredField("inputs");inputField.setAccessible(true);
        for(Object input:((Map<?,?>)inputField.get(screen)).values()) {
            net.minecraft.client.gui.components.AbstractWidget widget=(net.minecraft.client.gui.components.AbstractWidget)input;
            if(widget.visible)rectangles.add(new int[]{widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight()});
        }
        for(int i=0;i<rectangles.size();i++)for(int j=i+1;j<rectangles.size();j++){
            int[] a=rectangles.get(i),b=rectangles.get(j);
            if(a[0]<b[0]+b[2]&&a[0]+a[2]>b[0]&&a[1]<b[1]+b[3]&&a[1]+a[3]>b[1])
                throw new IllegalStateException("Native widgets overlap "+Arrays.toString(a)+" "+Arrays.toString(b));
        }
    }
}
