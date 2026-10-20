package org.sillon.qa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.*;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.builder.*;
import org.mtr.mod.client.*;
import org.mtr.mod.packet.*;
import org.mtr.mod.render.TrainSilhouetteCache;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.screen.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Separate QA mod: actual delivered jar, real simulation queue, native controls and GPU captures. */
public final class BuilderVisualQa {
    private static boolean active,fixtureReady;private static int stage,ticks;private static long sidingId,depotId;private static String railId,originalRail,changedRail;private static VehicleResource resource;private static ConsistBuilderScreen composer;private static BuilderRailScreen editor;private static DashboardScreen dashboard;
    public static void register(){MinecraftForge.EVENT_BUS.addListener(BuilderVisualQa::tick);}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static void call(Object object,String name)throws Exception{Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);}
    private static JsonReader id(NameColorDataBase value,long id){var json=Utilities.getJsonObjectFromData(value);json.addProperty("id",id);return new JsonReader(json);}
    private static Rail rail(Position a,Position b,int type){return type==1?Rail.newSidingRail(a,Angle.S,b,Angle.N,Rail.Shape.QUADRATIC,0,new ObjectArrayList<>(List.of(CustomResourceLoader.DEFAULT_RAIL_ID)),TransportMode.TRAIN):type==2?Rail.newPlatformRail(a,Angle.S,b,Angle.N,Rail.Shape.QUADRATIC,0,new ObjectArrayList<>(List.of(CustomResourceLoader.DEFAULT_RAIL_ID)),TransportMode.TRAIN):Rail.newRail(a,Angle.S,b,Angle.N,Rail.Shape.QUADRATIC,0,new ObjectArrayList<>(List.of(CustomResourceLoader.DEFAULT_RAIL_ID)),80,80,true,false,false,true,false,TransportMode.TRAIN);}
    public static void begin(Minecraft mc){try{
        active=true;stage=0;ticks=0;fixtureReady=false;resize(mc,false);mc.setScreen(null);mc.options.hideGui=false;
        List<VehicleResource> all=new ArrayList<>();CustomResourceLoader.iterateVehicles(TransportMode.TRAIN,all::add);check(!all.isEmpty(),"No real vehicle resources");resource=all.stream().filter(v->v.getId().contains("cm_stock")&&v.getLength()<25).findFirst().orElseGet(()->all.stream().filter(v->v.getLength()<24).findFirst().orElse(all.get(0)));
        ClientData data=new ClientData();UpdateDataRequest request=new UpdateDataRequest(data);
        Position a=new Position(80,4,0),b=new Position(80,4,100),c=new Position(80,4,140),d=new Position(80,4,260),e=new Position(80,4,300),f=new Position(80,4,420);
        Rail garage=rail(a,b,1),link=rail(b,c,0),pa=rail(c,d,2),link2=rail(d,e,0),pb=rail(e,f,2);for(Rail r:List.of(garage,link,pa,link2,pb)){data.rails.add(r);request.addRail(r);}railId=link.getHexId();originalRail=BuilderRailRequest.fingerprint(link);
        var platformA=new Platform(id(new Platform(c,d,TransportMode.TRAIN,data),733101),data);platformA.setName("1");var platformB=new Platform(id(new Platform(e,f,TransportMode.TRAIN,data),733102),data);platformB.setName("2");data.platforms.add(platformA);data.platforms.add(platformB);request.addPlatform(platformA).addPlatform(platformB);
        Position uc=new Position(80,20,140),ud=new Position(80,20,260);Rail upper=rail(uc,ud,2);data.rails.add(upper);request.addRail(upper);var upperPlatform=new Platform(id(new Platform(uc,ud,TransportMode.TRAIN,data),733103),data);upperPlatform.setName("3 · étage supérieur");data.platforms.add(upperPlatform);request.addPlatform(upperPlatform);
        var station=new Station(id(new Station(data),733104),data);station.setName("Manufacture");station.setColor(0x347D85);station.setCorners(new Position(65,0,130),new Position(96,30,270));data.stations.add(station);request.addStation(station);
        var stationB=new Station(id(new Station(data),733105),data);stationB.setName("Jardin des ateliers");stationB.setColor(0x8F6AAB);stationB.setCorners(new Position(65,0,290),new Position(96,30,430));data.stations.add(stationB);request.addStation(stationB);
        var route=new Route(id(new Route(TransportMode.TRAIN,data),733106),data);route.setName("Navette des ateliers");route.setRouteNumber("S1");route.setColor(0x347D85);route.getRoutePlatforms().add(new RoutePlatformData(platformA.getId()));route.getRoutePlatforms().add(new RoutePlatformData(platformB.getId()));route.getRoutePlatforms().add(new RoutePlatformData(platformA.getId()));data.routes.add(route);request.addRoute(route);
        var depot=new Depot(id(new Depot(TransportMode.TRAIN,data),733107),data);depot.setName("Atelier nord");depot.setCorners(new Position(70,0,-10),new Position(90,15,110));depot.getRouteIds().add(route.getId());data.depots.add(depot);request.addDepot(depot);depotId=depot.getId();
        var empty=new Depot(id(new Depot(TransportMode.TRAIN,data),733108),data);empty.setName("Atelier à compléter");empty.setCorners(new Position(100,0,190),new Position(120,15,210));data.depots.add(empty);request.addDepot(empty);
        var siding=new Siding(id(new Siding(a,b,100,TransportMode.TRAIN,data),733109),data);siding.setName("Navette · 2 voitures");var cars=new ObjectArrayList<VehicleCar>();for(int n=0;n<2;n++)cars.add(new VehicleCar(resource.getId(),resource.getLength(),resource.getWidth(),resource.getBogie1Position(),resource.getBogie2Position(),resource.getCouplingPadding1(),resource.getCouplingPadding2()));siding.setVehicleCars(cars);siding.setMaxVehicles(0);data.sidings.add(siding);request.addSiding(siding);sidingId=siding.getId();data.sync();
        mc.getSingleplayerServer().execute(()->{try{
            var server=mc.getSingleplayerServer();var level=server.overworld();for(int x=76;x<=87;x++)for(int z=140;z<=260;z++){level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,3,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());if(x<=78||x>=82)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,4,z),org.mtr.mod.Blocks.PLATFORM.get().data.defaultBlockState());}
            var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.teleportTo(level,84.5,5,242,175,35);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(org.mtr.mod.Items.BRUSH.get().data));
            Init.sendMessageC2S(OperationProcessor.UPDATE_DATA,new org.mtr.mapping.holder.MinecraftServer(server),new World(level),request,response->{PacketUpdateData.sendDirectlyToClientDepotUpdate(new ServerWorld(level),response);var generate=new DepotOperationByIds();generate.addDepotId(depotId);Init.sendMessageC2S(OperationProcessor.GENERATE_BY_DEPOT_IDS,new org.mtr.mapping.holder.MinecraftServer(server),new World(level),generate,null,null);mc.execute(()->fixtureReady=true);},UpdateDataResponse.class);
        }catch(Throwable error){error.printStackTrace();System.exit(2);}});
    }catch(Throwable error){error.printStackTrace();System.exit(2);}}
    private static void resize(Minecraft mc,boolean small){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),small?640:1120,small?480:720);mc.options.guiScale().set(2);mc.resizeDisplay();}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"builder-"+(Boolean.getBoolean("sillon.qa.shader")?"shader-":"native-")+name+".png",mc.getMainRenderTarget(),m->System.out.println("BUILDER_QA_CAPTURE "+m.getString()));}
    private static void controls(Minecraft mc){List<net.minecraft.client.gui.components.AbstractWidget> controls=new ArrayList<>();for(var c:mc.screen.children())if(c instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible){check(w.getX()>=0&&w.getY()>=0&&w.getWidth()>0&&w.getX()+w.getWidth()<=mc.screen.width&&w.getY()+w.getHeight()<=mc.screen.height,"Builder control outside viewport: "+w.getMessage().getString());for(var b:controls)check(!(w.getX()<b.getX()+b.getWidth()&&w.getX()+w.getWidth()>b.getX()&&w.getY()<b.getY()+b.getHeight()&&w.getY()+w.getHeight()>b.getY()),"Builder controls overlap: "+w.getMessage().getString());controls.add(w);}}
    private static void addCar()throws Exception{Method add=ConsistBuilderScreen.class.getDeclaredMethod("add",VehicleResource.class);add.setAccessible(true);add.invoke(composer,resource);}
    private static Siding siding(){return MinecraftClientData.getDashboardInstance().sidingIdMap.get(sidingId);}
    private static void next(){stage++;ticks=0;}
    private static void profilePixels()throws Exception{Field cache=TrainSilhouetteCache.class.getDeclaredField("CACHE");cache.setAccessible(true);int tested=0;for(Object e:((Map<?,?>)cache.get(null)).values()){if(!(boolean)field(e,"ready"))continue;var texture=(NativeImageBackedTexture)field(e,"texture");int old=GL33.glGetInteger(GL33.GL_TEXTURE_BINDING_2D);try{GL33.glBindTexture(GL33.GL_TEXTURE_2D,texture.getGlId());java.nio.ByteBuffer pixels=java.nio.ByteBuffer.allocateDirect(512*128*4);GL33.glGetTexImage(GL33.GL_TEXTURE_2D,0,GL33.GL_RGBA,GL33.GL_UNSIGNED_BYTE,pixels);int visible=0;for(int i=3;i<pixels.capacity();i+=4)if((pixels.get(i)&255)>0)visible++;check(visible>200,"GPU train profile is empty");tested++;}finally{GL33.glBindTexture(GL33.GL_TEXTURE_2D,old);}if(tested>=2)break;}check(tested>0,"No GPU profile verified");System.out.println("BUILDER_QA_GPU_ALPHA "+tested+" nonempty real model silhouettes");}
    private static void tick(TickEvent.ClientTickEvent event){if(!active||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;try{
        ticks++;if(ticks>650)throw new IllegalStateException("Builder stage timeout "+stage+" markers="+DoorGuideClient.markerCount()+" fixture="+fixtureReady);
        if(stage==0&&fixtureReady&&ticks>100){check(siding()!=null,"Actual siding update not received");if(DoorGuideClient.markerCount()>0){capture(mc,"doors");next();}else if(ticks%100==0)System.out.println("BUILDER_QA_WAITING_DOOR_PLANS "+ticks);}
        else if(stage==1&&ticks==10){mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY));}
        else if(stage==1&&ticks==35){check(DoorGuideClient.markerCount()==0,"Door guides remain without brush");composer=new ConsistBuilderScreen(siding(),null);mc.setScreen(composer);addCar();check(siding().getVehicleCars().size()==2,"Draft modified live siding");next();}
        else if(stage==2&&TrainSilhouetteCache.readyCount()>0&&ticks>90){controls(mc);profilePixels();capture(mc,"consist-large");set(composer,"name","QA navette");call(composer,"savePreset");check(ConsistPresets.load("TRAIN").get("QA navette").size()==3,"Preset not saved");resize(mc,true);next();}
        else if(stage==3&&ticks==35){controls(mc);capture(mc,"consist-small");composer.onClose2();check(siding().getVehicleCars().size()==2,"Cancel changed live siding");composer=new ConsistBuilderScreen(siding(),null);mc.setScreen(composer);addCar();call(composer,"apply");next();}
        else if(stage==4&&ticks==60){check(siding().getVehicleCars().size()==3,"Consist not acknowledged by real update packet");resize(mc,false);editor=new BuilderRailScreen(railId);mc.setScreen(editor);Rail draft=(Rail)field(editor,"draft");set(editor,"draft",Rail.copy(draft,new ObjectArrayList<String>()));check(BuilderRailRequest.fingerprint(MinecraftClientData.getInstance().railIdMap.get(railId)).equals(originalRail),"Rail draft mutated network");next();}
        else if(stage==5&&ticks==35){controls(mc);capture(mc,"rail-large");resize(mc,true);next();}
        else if(stage==6&&ticks==35){controls(mc);capture(mc,"rail-small");call(editor,"apply");next();}
        else if(stage==7&&ticks==70){check(((String)field(editor,"pending")).isEmpty(),"Rail update not acknowledged");Rail changed=MinecraftClientData.getInstance().railIdMap.get(railId);changedRail=BuilderRailRequest.fingerprint(changed);check(!changedRail.equals(originalRail),"Rail update not synchronized");call(editor,"undo");next();}
        else if(stage==8&&ticks==70){Rail restored=MinecraftClientData.getInstance().railIdMap.get(railId);check(BuilderRailRequest.fingerprint(restored).equals(originalRail),"Rail undo failed");Rail stale=Rail.copy(restored,new ObjectArrayList<String>());InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderRail(new BuilderRailRequest(stale,restored,"qa-conflict")));next();}
        else if(stage==9&&ticks==60){var receipt=PacketBuilderRail.take("qa-conflict");check(receipt!=null&&!receipt.success(),"Concurrent rail edit was not refused");resize(mc,false);dashboard=new DashboardScreen(TransportMode.TRAIN);mc.setScreen(dashboard);WidgetMap map=(WidgetMap)field(dashboard,"widgetMap");map.find(new Position(80,4,240));mc.setScreen(new BuilderDiagnosticsScreen(new Screen(dashboard),map::focusProblem));next();}
        else if(stage==10&&ticks==80){check(!BuilderDiagnosticsClient.issues.isEmpty(),"Actual diagnostic result missing");controls(mc);capture(mc,"diagnostics-large");resize(mc,true);next();}
        else if(stage==11&&ticks==35){controls(mc);capture(mc,"diagnostics-small");mc.setScreen(dashboard);WidgetMap map=(WidgetMap)field(dashboard,"widgetMap");double scale=(double)field(map,"scale"),cx=(double)field(map,"centerX");map.mouseScrolled2(290,110,.5);double nextScale=(double)field(map,"scale"),nextCx=(double)field(map,"centerX");check(Math.abs(cx+(290-map.getX2()-map.width/2D)/scale-nextCx-(290-map.getX2()-map.width/2D)/nextScale)<1e-7,"Native map cursor anchoring");map.find(new Position(80,4,240));next();}
        else if(stage==12&&ticks==50){capture(mc,"map-small");resize(mc,false);WidgetMap map=(WidgetMap)field(dashboard,"widgetMap");map.find(new Position(80,4,240));next();}
        else if(stage==13&&ticks==60){capture(mc,"map-large");WidgetMap map=(WidgetMap)field(dashboard,"widgetMap");double scale=(double)field(map,"scale"),cx=(double)field(map,"centerX"),cz=(double)field(map,"centerY");double x=map.getX2()+(80.5-cx)*scale+map.width/2D,y=map.getY2()+(200.5-cz)*scale+map.height/2D;map.mouseClicked2(x,y,0);check(mc.screen instanceof SavedRailPickerScreen,"Overlapping floor chooser did not open");next();}
        else if(stage==14&&ticks==35){controls(mc);capture(mc,"floors");mc.setScreen(new BuilderSettingsScreen(new Screen(dashboard)));next();}
        else if(stage==15&&ticks==35){controls(mc);capture(mc,"settings");String file=Boolean.getBoolean("sillon.qa.shader")?"builder-shader-qa-success.txt":"builder-native-qa-success.txt";Files.writeString(Path.of(file),"Actual planned train door overlays with no active train; brush removal; real GPU silhouettes alpha checked; consist cancel/apply and presets; rail staged preview/apply/server ack/undo/conflict; server diagnostics; anchored map zoom and stacked floor chooser; native controls at 560x360 and 320x240.");System.out.println("BUILDER_QA_SUCCESS");active=false;mc.stop();}
    }catch(Throwable error){System.err.println("BUILDER_QA_FAILED stage="+stage);error.printStackTrace();System.exit(2);}}
}
