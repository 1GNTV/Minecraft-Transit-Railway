package org.sillon.qa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.mtr.core.data.*;
import org.mtr.core.data.Position;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.*;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.tool.Utilities;
import org.mtr.mapping.mapper.PersistenceStateExtension;
import org.mtr.mod.*;
import org.mtr.mod.block.BlockStationDecor;
import org.mtr.mod.builder.*;
import org.mtr.mod.client.*;
import org.mtr.mod.data.*;
import org.mtr.mod.decor.DecorKind;
import org.mtr.mod.packet.*;
import org.mtr.mod.screen.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Additional contracts run in the actual client/server with the assembled jar and optional Oculus. */
public final class BuilderNextVisualQa {
    private static boolean active;private static volatile boolean serverPolygon,worldReady,graphSaved,serverCars;
    private static int stage,ticks;
    private static PolygonAreaScreen polygon;
    private static JourneyScreen journey;
    private static ConsistBuilderScreen composer;
    private static long platformId,sidingId;
    private static SillonSignageSpec guidedSpec;
    private static final net.minecraft.core.BlockPos A=new net.minecraft.core.BlockPos(84,5,242),B=new net.minecraft.core.BlockPos(84,5,248);
    public static void register(){MinecraftForge.EVENT_BUS.addListener(BuilderNextVisualQa::tick);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static void call(Object object,String name)throws Exception{Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);}
    private static void resize(Minecraft mc,boolean small){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),small?640:1120,small?480:720);mc.options.guiScale().set(2);mc.resizeDisplay();}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"builder-"+(Boolean.getBoolean("sillon.qa.shader")?"shader-":"native-")+name+".png",mc.getMainRenderTarget(),m->System.out.println("BUILDER_NEXT_CAPTURE "+m.getString()));}
    private static void controls(Minecraft mc){List<net.minecraft.client.gui.components.AbstractWidget> seen=new ArrayList<>();for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible){check(w.getX()>=0&&w.getY()>=0&&w.getWidth()>0&&w.getX()+w.getWidth()<=mc.screen.width&&w.getY()+w.getHeight()<=mc.screen.height,"Next UI control outside viewport: "+w.getMessage().getString());for(var other:seen)check(!(w.getX()<other.getX()+other.getWidth()&&w.getX()+w.getWidth()>other.getX()&&w.getY()<other.getY()+other.getHeight()&&w.getY()+w.getHeight()>other.getY()),"Next UI controls overlap: "+w.getMessage().getString());seen.add(w);}}
    private static void next(){stage++;ticks=0;}
    private static Siding siding(){return MinecraftClientData.getDashboardInstance().sidingIdMap.get(sidingId);}
    private static void list(Minecraft mc,java.util.function.Consumer<ClientData> callback){
        var server=mc.getSingleplayerServer();
        server.execute(()->Init.sendMessageC2S(OperationProcessor.LIST_DATA,new org.mtr.mapping.holder.MinecraftServer(server),new org.mtr.mapping.holder.World(server.overworld()),new BuilderPlanRequest.Response(""),response->{ClientData data=new ClientData();new ListDataResponse(new JsonReader(Utilities.getJsonObjectFromData(response)),data).write();callback.accept(data);},ListDataResponse.class));
    }
    private static void polygonContracts(){
        ClientData data=new ClientData();Station station=new Station(data);station.setName("Zone en L");station.setCorners(new Position(0,0,0),new Position(20,10,20));
        AreaShape.apply(station,RegionPolygon.decode("0,0;20,0;20,6;6,6;6,20;0,20"));
        Platform inside=new Platform(new Position(3,4,12),new Position(3,4,18),TransportMode.TRAIN,data),outside=new Platform(new Position(15,4,12),new Position(15,4,18),TransportMode.TRAIN,data);
        data.stations.add(station);data.platforms.add(inside);data.platforms.add(outside);data.sync();
        check(inside.area==station&&outside.area==null,"Real MTR station association ignores polygon notch");
        var restored=new Station(new JsonReader(Utilities.getJsonObjectFromData(station)),data);
        check(AreaShape.polygon(restored)!=null&&!restored.inArea(new Position(15,4,15)),"Polygon serializer round trip");
        AreaShape.apply(restored,null);check(restored.inArea(new Position(15,4,15)),"Explicit rectangle mode restores legacy containment");
        System.out.println("BUILDER_NEXT_MTR_POLYGON actual association and serializer verified");
    }
    public static void begin(Minecraft mc){
        try{
            active=true;stage=0;ticks=0;serverPolygon=worldReady=graphSaved=serverCars=false;polygonContracts();resize(mc,false);
            var data=MinecraftClientData.getDashboardInstance();var station=data.stationIdMap.get(733104);check(station!=null,"Fixture station absent");
            platformId=data.platforms.stream().filter(p->p.area!=null&&p.area.getId()==733104&&p.getMidPosition().getY()==4).findFirst().orElseThrow().getId();
            sidingId=data.sidings.stream().filter(s->s.getMidPosition().getX()==80&&s.getMidPosition().getZ()==50).findFirst().orElseThrow().getId();
            polygon=new PolygonAreaScreen(station,new DashboardScreen(TransportMode.TRAIN));mc.setScreen(polygon);
            @SuppressWarnings("unchecked") List<RegionPolygon.Point> points=(List<RegionPolygon.Point>)field(polygon,"points");points.clear();points.addAll(RegionPolygon.decode("65,130;96,130;96,180;85,180;85,270;65,270").points());
        }catch(Throwable error){error.printStackTrace();System.exit(2);}
    }
    private static void createWalks(Minecraft mc){
        var server=mc.getSingleplayerServer();server.execute(()->{
            try{
                var level=server.overworld();var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                player.teleportTo(level,84.5,6,242,0,0);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.mtr.mod.Items.BRUSH.get().data));
                var store=(PersistentStateData)PersistenceStateExtension.register(new org.mtr.mapping.holder.ServerWorld(level),PersistentStateData::new,Init.MOD_ID);
                for(var pos:List.of(A,B)){store.walking().remove(pos.asLong());level.removeBlock(pos,false);level.setBlockAndUpdate(pos,org.mtr.mod.Blocks.STATION_DECOR.get(DecorKind.WAYPOINT).get().data.defaultBlockState());}
                store.walkingChanged();mc.execute(()->worldReady=true);
            }catch(Throwable error){error.printStackTrace();System.exit(2);}
        });
    }
    private static void saveNode(net.minecraft.core.BlockPos pos,long platform){WalkGraph graph=new WalkGraph();graph.put(new WalkGraph.Node(pos.asLong(),pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5,platform,platform==0?"Couloir des ateliers":"Quai navette",platform==0?"passage":"platform",0,List.of()));InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderWayfinding(1,pos.asLong(),0,graph.encode()));}
    private static void guidance(){
        var data=SillonSignageNetwork.snapshot();var spec=new SillonSignageSpec("Correspondances","","","direction","","wall",-1,0x101D56,0xFFFFFF,0x347D85,2,.6f,0,0,0,true,false,false).withBinding(new SillonSignageBinding(733104,platformId,0,"auto","full","auto","","auto",true,true,true));
        guidedSpec=spec;
        var context=new SillonSignageAutomatic.Context(new SillonSignageNetworkSnapshot.Point(84.5,5.1,242.5),733104,0,0);
        var result=SignageWayfinding.resolve(new SillonSignageAutomatic.Result(spec,"",733104,platformId,0),data,context);
        int expected=SillonSignageAutomatic.arrow(context,new SillonSignageNetworkSnapshot.Point(84.5,5.1,248.5));
        check(result.status().contains("Guidage")&&result.document().rows().stream().anyMatch(r->r.arrow()==expected),"Panel does not follow authored first edge: "+result.status()+" rows="+result.document().rows()+" expected="+expected+" graph="+WayfindingClient.graph().encode());
        System.out.println("BUILDER_NEXT_GUIDANCE panel follows walking graph instead of direct platform bearing");
    }
    private static void tick(TickEvent.ClientTickEvent event){
        if(!active||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        try{
            if(++ticks>500)throw new IllegalStateException("Next builder stage timeout "+stage+" feedback="+WayfindingClient.feedback);
            if(stage==0&&ticks==30){controls(mc);capture(mc,"polygon-large");resize(mc,true);next();}
            else if(stage==1&&ticks==30){controls(mc);capture(mc,"polygon-small");polygon.onClose2();list(mc,data->{check(AreaShape.polygon(data.stationIdMap.get(733104))!=null&&!data.stationIdMap.get(733104).inArea(new Position(90,4,230)),"Polygon not stored on real simulation server");serverPolygon=true;});createWalks(mc);next();}
            else if(stage==2&&serverPolygon&&worldReady&&ticks>30){saveNode(A,0);saveNode(B,platformId);next();}
            else if(stage==3&&WayfindingClient.graph().get(A.asLong())!=null&&WayfindingClient.graph().get(B.asLong())!=null&&ticks>30){
                mc.getSingleplayerServer().execute(()->{var player=new org.mtr.mapping.holder.ServerPlayerEntity(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));PacketBuilderWayfinding.select(player,new org.mtr.mapping.holder.BlockPos(A));PacketBuilderWayfinding.select(player,new org.mtr.mapping.holder.BlockPos(B));});
                next();
            }else if(stage==4&&!WayfindingClient.graph().path(A.asLong(),platformId,false).isEmpty()&&ticks>30){
                var network=SillonSignageNetwork.snapshot();
                if(network.platforms().stream().noneMatch(p->p.id()==platformId)||network.lines().stream().noneMatch(l->l.stops().stream().anyMatch(stop->stop.platformId()==platformId))){if(ticks%40==0)System.out.println("BUILDER_NEXT_WAITING_NETWORK platforms="+network.platforms().size()+" lines="+network.lines().size()+" target="+platformId);return;}
                mc.getSingleplayerServer().execute(()->{var store=(PersistentStateData)PersistenceStateExtension.register(new org.mtr.mapping.holder.ServerWorld(mc.getSingleplayerServer().overworld()),PersistentStateData::new,Init.MOD_ID);var nbt=new org.mtr.mapping.holder.CompoundTag();store.writeNbt2(nbt);var restored=new PersistentStateData();restored.readNbt(nbt);check(!restored.walking().path(A.asLong(),platformId,false).isEmpty(),"Walking graph NBT reload lost links");graphSaved=true;});
                guidance();resize(mc,false);mc.setScreen(null);mc.options.hideGui=true;
                mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();var panel=new net.minecraft.core.BlockPos(84,7,242);level.setBlockAndUpdate(panel,org.mtr.mod.Blocks.SILLON_SIGNAGE.get().data.defaultBlockState().setValue(org.mtr.mapping.mapper.DirectionHelper.FACING.data,net.minecraft.core.Direction.SOUTH));((org.mtr.mod.block.BlockSillonSignage.BlockEntity)level.getBlockEntity(panel)).setDocument(guidedSpec);mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).teleportTo(level,84.5,7,237,0,23);});
                stage=40;ticks=0;
            }else if(stage==40&&ticks==40){capture(mc,"walking-guidance-world");mc.options.hideGui=false;mc.setScreen(new WaypointScreen(new org.mtr.mapping.holder.BlockPos(B)));stage=5;ticks=0;}else if(stage==5&&ticks==30){controls(mc);capture(mc,"waypoint-large");resize(mc,true);next();}
            else if(stage==6&&ticks==30){controls(mc);capture(mc,"waypoint-small");mc.setScreen(null);resize(mc,false);journey=new JourneyScreen(null);mc.setScreen(journey);next();}
            else if(stage==7&&ticks==60){controls(mc);capture(mc,"journey-search");set(journey,"origin",733104L);set(journey,"destination",733105L);call(journey,"request");next();}
            else if(stage==8&&field(journey,"response")!=null&&ticks>20){
                var response=(org.mtr.libraries.com.google.gson.JsonObject)field(journey,"response");check(response.has("legs")||response.has("error")&&response.get("error").getAsString().startsWith("Aucun itinéraire"),"Native journey engine failed: "+response);if(response.has("legs")){var legs=response.getAsJsonArray("legs");check(!legs.isEmpty(),"Native walking fixture returned no journey");for(var item:legs){var leg=item.getAsJsonObject();check(!leg.get("from").getAsString().equals("À pied"),"Walking step lost origin label");check(!(leg.get("from").equals(leg.get("to"))&&leg.get("startTime").equals(leg.get("endTime"))&&leg.get("walkingDistance").getAsLong()==0),"Zero-duration arrival marker leaked into journey cards");}}controls(mc);capture(mc,"journey-result-large");resize(mc,true);next();
            }else if(stage==9&&ticks==30){controls(mc);capture(mc,"journey-result-small");journey.onClose2();resize(mc,false);composer=new ConsistBuilderScreen(siding(),null);mc.setScreen(composer);
                @SuppressWarnings("unchecked") List<VehicleCar> draft=(List<VehicleCar>)field(composer,"draft");VehicleCar car=draft.get(0);draft.add(car);composer.onClose2();check(mc.screen==null,"Consist Escape validation failed: "+field(composer,"message"));next();
            }else if(stage==10&&!serverCars&&ticks%20==0){list(mc,data->{int count=data.sidingIdMap.get(sidingId).getVehicleCars().size();if(count==4)serverCars=true;else System.out.println("BUILDER_NEXT_WAITING_CONSIST_SERVER cars="+count);});
            }else if(stage==10&&serverCars&&graphSaved&&ticks>30){
                composer=new ConsistBuilderScreen(siding(),null);mc.setScreen(composer);
                @SuppressWarnings("unchecked") ConsistDraft<VehicleCar> edit=(ConsistDraft<VehicleCar>)field(composer,"edit");edit.select(0,false,false);edit.select(1,true,false);
                composer.mouseClicked2(20,mc.screen.height-95,0);composer.mouseDragged2(100,20,0,80,-120);GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),200,40);next();
            }else if(stage==11&&ticks==25){capture(mc,"consist-trash");composer.mouseReleased2(100,20,0);
                @SuppressWarnings("unchecked") List<VehicleCar> draft=(List<VehicleCar>)field(composer,"draft");check(draft.size()==2,"Grouped trash deletion failed");
                @SuppressWarnings("unchecked") ConsistDraft<VehicleCar> edit=(ConsistDraft<VehicleCar>)field(composer,"edit");edit.undo();check(draft.size()==4,"Grouped deletion undo failed");
                call(composer,"discard");String flag=Boolean.getBoolean("sillon.qa.shader")?"builder-next-shader-qa-success.txt":"builder-next-native-qa-success.txt";
                Files.writeString(Path.of(flag),"Native MTR polygon association, legacy fallback, serialization and real server update; polygon editor at two sizes; real walking node packets and linking, NBT reload and guided panel arrow; native asynchronous MTR journey result and terminal layouts; consist Escape server persistence; grouped drag-to-trash and undo.");System.out.println("BUILDER_NEXT_QA_SUCCESS");active=false;mc.stop();
            }
        }catch(Throwable error){System.err.println("BUILDER_NEXT_QA_FAILED stage="+stage);error.printStackTrace();System.exit(2);}
    }
}
