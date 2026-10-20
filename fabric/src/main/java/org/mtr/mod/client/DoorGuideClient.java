package org.mtr.mod.client;

import org.mtr.core.data.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.Items;
import org.mtr.mod.block.PlatformHelper;
import org.mtr.mod.builder.*;
import org.mtr.mod.packet.PacketBuilderPlan;
import org.mtr.mod.render.*;
import org.mtr.mod.resource.*;
import java.util.*;

/** Automatic, read-only, brush-held platform guide. Does not open PSDs or tick a live train. */
public final class DoorGuideClient {
    private record Car(VehicleCar car,PositionAndRotation pose,int index,int count,int color){}
    private record Marker(List<DoorGuideGeometry.Point> polygon,double y,int color){}
    private static List<Car> cars=List.of();private static List<Marker> markers=List.of();private static List<String> labels=List.of();
    private static final int[] COLORS={0xFF61D4C4,0xFFFFBD68,0xFF91A9FF,0xFFEE91C5,0xFFA6D57B,0xFF7FCAFF};
    private static Object owner;private static long requested,received,built;private static boolean active,approximate;
    public static int markerCount(){return markers.size();}
    public static void tick(){var mc=MinecraftClient.getInstance();var p=mc.getPlayerMapped();var w=mc.getWorldMapped();if(owner!=(w==null?null:w.data)){owner=w==null?null:w.data;cars=List.of();markers=List.of();labels=List.of();requested=received=0;}
        active=w!=null&&p!=null&&BuilderPreferences.enabled("doorGuides")&&new PlayerEntity(p.data).isHolding(Items.BRUSH.get().asItem());
        if(!active){markers=List.of();return;}long now=System.currentTimeMillis();if(now-requested>2500){requested=now;InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketBuilderPlan(p.getX(),p.getY(),p.getZ()));}
        if(now-received>7000){markers=List.of();return;}if(now-built>350){built=now;rebuild();}
    }
    public static void accept(String raw){if(!active||raw.length()>28000)return;try{JsonArray plans=Utilities.parseJson(raw).getAsJsonArray();List<Car> next=new ArrayList<>();List<String> names=new ArrayList<>();int group=0;for(JsonElement e:plans){JsonObject plan=e.getAsJsonObject();JsonArray list=plan.getAsJsonArray("cars");int color=COLORS[group++%COLORS.length];names.add(plan.get("label").getAsString());for(int i=0;i<list.size();i++){var entry=list.get(i).getAsJsonObject();VehicleCar car=new VehicleCar(new JsonReader(entry.get("car")));ObjectArrayList<PositionAndRotation> bogies=new ObjectArrayList<>();for(var b:entry.getAsJsonArray("bogies")){var a=b.getAsJsonArray();bogies.add(PositionAndRotation.geometry(new org.mtr.core.tool.Vector(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()),new org.mtr.core.tool.Vector(a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble()),true));}next.add(new Car(car,new PositionAndRotation(bogies,car,true,false),i,list.size(),color));}}cars=List.copyOf(next);labels=List.copyOf(names);received=System.currentTimeMillis();built=0;}catch(RuntimeException error){Init.LOGGER.warn("Sillon ignored invalid door guide snapshot",error);}}
    private static void rebuild(){var mc=MinecraftClient.getInstance();var world=mc.getWorldMapped();var player=mc.getPlayerMapped();if(world==null||player==null)return;List<Marker> result=new ArrayList<>();approximate=false;
        for(Car c:cars){if(Math.hypot(c.pose.position.x-player.getX(),c.pose.position.z-player.getZ())>100)continue;CustomResourceLoader.getVehicleById(TransportMode.TRAIN,c.car.getVehicleId(),details->{VehicleResource resource=details.left();resource.prepareModels(c.index,c.count);VehicleResourceCache cache=resource.getCachedVehicleResource(c.index,c.count,false);if(cache==null)return;approximate|=resource.hasEstimatedDoorways();for(Box door:cache.doorways){
            double sign=(door.getMinXMapped()+door.getMaxXMapped())>=0?1:-1,x0=sign>0?door.getMinXMapped():door.getMinXMapped()-.8,x1=sign>0?door.getMaxXMapped()+.8:door.getMaxXMapped();
            var points=new ArrayList<DoorGuideGeometry.Point>();double yy=0;for(double[] v:new double[][]{{x0,door.getMinZMapped()},{x0,door.getMaxZMapped()},{x1,door.getMaxZMapped()},{x1,door.getMinZMapped()}}){var p=c.pose.transformForwards(new Vector3d(v[0],door.getMaxYMapped(),v[1]),Vector3d::rotateX,Vector3d::rotateY,Vector3d::add);points.add(new DoorGuideGeometry.Point(p.getXMapped(),p.getZMapped()));yy+=p.getYMapped()/4;}
            int minX=(int)Math.floor(points.stream().mapToDouble(DoorGuideGeometry.Point::x).min().orElse(0)),maxX=(int)Math.floor(points.stream().mapToDouble(DoorGuideGeometry.Point::x).max().orElse(0)),minZ=(int)Math.floor(points.stream().mapToDouble(DoorGuideGeometry.Point::z).min().orElse(0)),maxZ=(int)Math.floor(points.stream().mapToDouble(DoorGuideGeometry.Point::z).max().orElse(0));
            if((maxX-minX+1L)*(maxZ-minZ+1)>64)continue;
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=(int)Math.floor(yy)-2;y<=(int)Math.floor(yy);y++){BlockPos at=new BlockPos(x,y,z);if(!Init.isChunkLoaded(new World(world.data),at)||!(world.getBlockState(at).getBlock().data instanceof PlatformHelper))continue;var poly=DoorGuideGeometry.clip(points,x,z);if(poly.size()>=3&&DoorGuideGeometry.area(poly)>.002)result.add(new Marker(poly,y+1.015,c.color));break;}
        }});if(result.size()>4096)break;}markers=List.copyOf(result.subList(0,Math.min(4096,result.size())));
    }
    public static void render(){if(!active||markers.isEmpty())return;for(Marker m:markers){MainRenderer.scheduleRender(QueuedRenderLayer.LIGHT,(g,offset)->{var p=m.polygon;for(int i=1;i<p.size()-1;i++){var a=p.get(0);var b=p.get(i);var c=p.get(i+1);g.drawTextureInWorld((float)(a.x()-offset.getXMapped()),(float)(m.y-offset.getYMapped()),(float)(a.z()-offset.getZMapped()),(float)(b.x()-offset.getXMapped()),(float)(m.y-offset.getYMapped()),(float)(b.z()-offset.getZMapped()),(float)(c.x()-offset.getXMapped()),(float)(m.y-offset.getYMapped()),(float)(c.z()-offset.getZMapped()),(float)(c.x()-offset.getXMapped()),(float)(m.y-offset.getYMapped()),(float)(c.z()-offset.getZMapped()),0,0,1,1,Direction.UP,m.color&0xFFFFFF,150,GraphicsHolder.getDefaultLight());}});}}
    public static void hud(GraphicsHolder g){var mc=MinecraftClient.getInstance();if(!active||mc.getCurrentScreenMapped()!=null)return;int y=12;if(!markers.isEmpty()){BuilderUi.text(g,"Portes à l’arrêt"+(approximate?" · modèle approximatif":""),8,y,BuilderUi.TEXT);for(int i=0;i<Math.min(4,labels.size());i++)BuilderUi.text(g,BuilderUi.fit(labels.get(i),300),8,y+13+i*12,COLORS[i%COLORS.length]);}}
}
