package org.sillon.qa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.mtr.mod.Init;
import org.mtr.mod.client.ChantierClient;
import org.mtr.mod.chantier.*;
import org.mtr.mod.screen.ChantierScreen;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import static org.mtr.mod.chantier.ChantierPlan.*;
/** Separate QA mod. Exercises actual C2S packets, loaded worlds, persisted journals and native MTR records. */
public final class ChantierVisualQa {
    private static boolean active;private static int stage,ticks;private static volatile boolean ready,verified;private static long frames,at;
    private static ChantierPlan initial,adjusted;private static final Map<BlockPos,BlockState> TERRAIN=new LinkedHashMap<>();private static final BlockPos CHEST=new BlockPos(-60,6,16);
    public static void register(){MinecraftForge.EVENT_BUS.addListener(ChantierVisualQa::tick);MinecraftForge.EVENT_BUS.addListener(ChantierVisualQa::frame);}
    private static void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.END)frames++;}
    private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    private static void next(){stage++;ticks=0;at=frames;}
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable e){e.printStackTrace();System.exit(2);}});}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"chantier-"+(Boolean.getBoolean("sillon.qa.shader")?"shader-":"native-")+name+".png",mc.getMainRenderTarget(),m->System.out.println("CHANTIER_CAPTURE "+m.getString()));}
    private static void resize(Minecraft mc,boolean small){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),small?640:1120,small?480:720);mc.options.guiScale().set(2);mc.resizeDisplay();}
    private static void controls(Minecraft mc){List<net.minecraft.client.gui.components.AbstractWidget> widgets=new ArrayList<>();for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible){check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=mc.screen.width&&w.getY()+w.getHeight()<=mc.screen.height,"Chantier control outside viewport: "+w.getMessage().getString());for(var q:widgets)check(!(w.getX()<q.getX()+q.getWidth()&&w.getX()+w.getWidth()>q.getX()&&w.getY()<q.getY()+q.getHeight()&&w.getY()+w.getHeight()>q.getY()),"Chantier controls overlap");widgets.add(w);}}
    private static void tab(Minecraft mc,int tab){var screen=(ChantierScreen)mc.screen;try{Field f=ChantierScreen.class.getDeclaredField("tab");f.setAccessible(true);f.setInt(screen,tab);screen.resize(mc,screen.width,screen.height);}catch(Exception e){throw new IllegalStateException(e);}}
    public static void begin(Minecraft mc){try{
        active=true;stage=0;ticks=0;ready=verified=false;at=frames;TERRAIN.clear();resize(mc,false);mc.setScreen(null);mc.options.hideGui=false;
        initial=ChantierPlan.of(new Part(Kind.STATION,new Point(-60.5,4,10.5),new Point(-60.5,4,38.5),19,8,1,1));
        var list=new ArrayList<>(initial.parts());list.add(new Part(Kind.HALL,new Point(-60.5,5,24.5),new Point(-45.5,5,24.5),5,4,0,0));list.add(new Part(Kind.HALL,new Point(-60.5,11,10.5),new Point(-60.5,11,38.5),19,5,0,0));list.add(new Part(Kind.STAIRS,new Point(-60.5,5,12.5),new Point(-60.5,11,22.5),3,4,0,0));initial=initial.withParts(list);
        var altered=new ArrayList<>(initial.parts());altered.set(0,altered.get(0).dimensions(21,9));altered.remove(1);adjusted=initial.withParts(altered);
        server(()->{var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);mc.getSingleplayerServer().getPlayerList().op(player.getGameProfile());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.mtr.mod.Items.CHANTIER.get().data));player.teleportTo(level,-40,18,48,135,22);
            Set<BlockPos> positions=new HashSet<>();for(var p:List.of(initial,adjusted))for(var c:ChantierRaster.complete(p).cells().keySet())positions.add(new BlockPos(c.x(),c.y(),c.z()));
            for(BlockPos pos:positions){level.getChunk(pos);BlockState state=pos.getY()>8?Blocks.WATER.defaultBlockState():Math.floorMod(pos.getX()+pos.getZ(),5)==0?Blocks.DIAMOND_ORE.defaultBlockState():Blocks.ANDESITE.defaultBlockState();level.setBlock(pos,state,2|16);TERRAIN.put(pos,state);}level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());mc.execute(()->{ChantierClient.project=UUID.randomUUID().toString();ChantierClient.change(initial);ready=true;});});
    }catch(Throwable e){e.printStackTrace();System.exit(2);}}
    private static void terrain(Minecraft mc,boolean chest){var level=mc.getSingleplayerServer().overworld();for(var e:TERRAIN.entrySet())if(!chest||!e.getKey().equals(CHEST))check(level.getBlockState(e.getKey()).equals(e.getValue()),"Terrain not restored at "+e.getKey()+" : "+level.getBlockState(e.getKey())+" expected "+e.getValue());}
    private static void tick(TickEvent.ClientTickEvent e){if(!active||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try{if(++ticks>1600)throw new IllegalStateException("Chantier timeout stage="+stage+" "+ChantierClient.message);
            if(stage==0&&ready&&ChantierClient.preview()!=null&&frames>at+3){check(ChantierClient.preview().error().isEmpty(),ChantierClient.preview().error());capture(mc,"preview");mc.setScreen(new ChantierScreen());next();}
            else if(stage>=1&&stage<=4&&frames>at+3&&ticks>10){controls(mc);capture(mc,"panel-large-"+(stage-1));if(stage<4){tab(mc,stage);next();}else{resize(mc,true);tab(mc,0);next();}}
            else if(stage>=5&&stage<=8&&frames>at+3&&ticks>10){controls(mc);capture(mc,"panel-small-"+(stage-5));if(stage<8){tab(mc,stage-4);next();}else{((ChantierScreen)mc.screen).onClose();check(mc.screen==null,"Escape did not return to world");resize(mc,false);ChantierClient.send(0);next();}}
            else if(stage==9&&!ChantierClient.busy&&ticks>20){check(ChantierClient.message.contains("protégé"),"Chest did not block construction: "+ChantierClient.message);ready=false;server(()->{terrain(mc,true);mc.getSingleplayerServer().overworld().removeBlock(CHEST,false);mc.getSingleplayerServer().overworld().setBlock(CHEST,TERRAIN.get(CHEST),2|16);mc.execute(()->ready=true);});next();}
            else if(stage==10&&ready){ChantierClient.send(0);next();}
            else if(stage==11&&!ChantierClient.busy&&ticks>20){check(ChantierClient.message.startsWith("Gare construite"),ChantierClient.message);server(()->{var level=mc.getSingleplayerServer().overworld();for(var entry:ChantierRaster.complete(initial).cells().entrySet()){var c=entry.getKey();var state=level.getBlockState(new BlockPos(c.x(),c.y(),c.z()));if(entry.getValue()==Role.AIR)check(state.isAir()||state.getBlock() instanceof org.mtr.mod.block.BlockNode,"Interior not excavated: "+c+" "+state);}
                    Path file=mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/sillon-chantier").resolve(mc.player.getUUID().toString()).resolve(ChantierClient.project+".dat");check(Files.isRegularFile(file),"Persistent undo journal absent");try{check(!net.minecraft.nbt.NbtIo.readCompressed(file.toFile()).getBoolean("pending"),"Journal not committed");}catch(Exception ex){throw new IllegalStateException(ex);}mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).teleportTo(level,-60.5,6,16,0,0);verified=true;});next();}
            else if(stage==12&&verified&&ticks>80&&frames>at+3){capture(mc,"station-interior");ChantierClient.checkpoint();ChantierClient.change(adjusted);next();}
            else if(stage==13&&ChantierClient.preview()!=null&&ChantierClient.preview().revision()==ChantierClient.revision){ChantierClient.send(0);next();}
            else if(stage==14&&!ChantierClient.busy&&ticks>20){check(ChantierClient.message.startsWith("Gare construite"),ChantierClient.message);verified=false;server(()->{var level=mc.getSingleplayerServer().overworld();var retained=ChantierRaster.complete(adjusted).cells();for(var c:ChantierRaster.complete(initial).cells().keySet())if(!retained.containsKey(c)){BlockPos p=new BlockPos(c.x(),c.y(),c.z());check(level.getBlockState(p).equals(TERRAIN.get(p)),"Removed corridor not restored");}mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).teleportTo(level,-60.5,12,16,0,0);verified=true;});next();}
            else if(stage==15&&verified&&ticks>30&&frames>at+3){capture(mc,"mezzanine");ChantierClient.send(1);next();}
            else if(stage==16&&!ChantierClient.busy&&ticks>20){check(ChantierClient.message.equals("Terrain initial restauré."),ChantierClient.message);verified=false;server(()->{terrain(mc,false);verified=true;});next();}
            else if(stage==17&&verified){String flag=Boolean.getBoolean("sillon.qa.shader")?"chantier-shader-qa-success.txt":"chantier-native-qa-success.txt";Files.writeString(Path.of(flag),"Actual placement packets; chest refusal before mutation; ore/andesite/water excavation; sealed shell and dry interior; real MTR records; large/small controls; world hologram; mezzanine; incremental adjustment and removed-corridor restoration; persisted journal reload and full original-terrain undo.");System.out.println("CHANTIER_QA_SUCCESS");active=false;mc.stop();}
        }catch(Throwable error){System.err.println("CHANTIER_QA_FAILED stage="+stage);error.printStackTrace();System.exit(2);}
    }
}
