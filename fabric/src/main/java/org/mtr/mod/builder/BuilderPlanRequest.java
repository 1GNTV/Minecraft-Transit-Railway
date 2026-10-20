package org.mtr.mod.builder;

import org.mtr.core.data.*;
import org.mtr.core.serializer.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.*;
import org.mtr.mod.mixin.SidingPlanningAccessor;
import java.util.*;

/** Runs on the transport simulation queue. Only detached vehicles are positioned. */
public final class BuilderPlanRequest implements SerializedDataBase {
    public static final String KEY="sillon/builder/door-plans";
    public double x,y,z;public String mode="doors";
    public BuilderPlanRequest(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    public BuilderPlanRequest(ReaderBase r){updateData(r);}
    @Override public void updateData(ReaderBase r){r.unpackString("mode",v->mode=v);r.unpackDouble("x",v->x=v);r.unpackDouble("y",v->y=v);r.unpackDouble("z",v->z=v);}
    @Override public void serializeData(WriterBase w){w.writeString("mode",mode);w.writeDouble("x",x);w.writeDouble("y",y);w.writeDouble("z",z);}
    public static final class Response implements SerializedDataBase {
        private final String plan;
        public Response(String plan){this.plan=plan;}
        @Override public void updateData(ReaderBase r){}
        @Override public void serializeData(WriterBase w){w.writeString("plan",plan);}
    }
    private static final class Parked extends Vehicle {
        Parked(VehicleExtraData extra,Siding siding,Data data){super(extra,null,siding.getTransportMode(),data);}
        void park(double distance,boolean backwards){railProgress=distance;reversed=backwards;}
    }
    public Response collect(Simulator data){
        if(mode.equals("diagnostics")){var issues=new java.util.ArrayList<>(NetworkDiagnostics.inspect(data));String json=NetworkDiagnostics.encode(issues).toString();while(json.length()>27000&&!issues.isEmpty()){issues.remove(issues.size()-1);json=NetworkDiagnostics.encode(issues).toString();}return new Response(json);}
        JsonArray plans=new JsonArray();Set<String> seen=new HashSet<>();
        var nearby=new ArrayList<Platform>();for(Platform p:data.platforms){var c=p.getMidPosition();if(p.getTransportMode()==TransportMode.TRAIN&&Math.abs(c.getY()-y)<20&&Math.hypot(c.getX()-x,c.getZ()-z)<96)nearby.add(p);}
        nearby.sort(Comparator.comparingDouble(p->Math.hypot(p.getMidPosition().getX()-x,p.getMidPosition().getZ()-z)));
        for(Platform platform:nearby.subList(0,Math.min(4,nearby.size())))for(Siding siding:data.sidings){
            if(plans.size()>=12)break;
            if(siding.getTransportMode()!=TransportMode.TRAIN||siding.area==null||siding.getVehicleCars().isEmpty()||!((Object)siding instanceof SidingPlanningAccessor))continue;
            var a=(SidingPlanningAccessor)(Object)siding;var depot=(Depot)siding.area;if(depot.getPath().stream().noneMatch(path->path.getSavedRailBaseId()==platform.getId()))continue;
            if(a.sillon$defaultPath()==null||a.sillon$main().isEmpty()||a.sillon$approach().isEmpty())continue;
            var extra=VehicleExtraData.create(depot.getId(),siding.getId(),siding.getRailLength(),siding.getVehicleCars(),a.sillon$approach(),a.sillon$main(),a.sillon$return(),a.sillon$defaultPath(),depot.getRepeatInfinitely(),siding.getAcceleration(),siding.getDeceleration(),siding.getIsManual(),siding.getMaxManualSpeed(),siding.getManualToAutomaticTime());
            boolean reversed=false;PathData prev=null;
            for(PathData path:extra.immutablePath){
                if(prev!=null&&prev.isOppositeRail(path))reversed=!reversed;
                prev=path;
                if(path.getSavedRailBaseId()!=platform.getId()||path.getDwellTime()<=0)continue;
                String key=platform.getId()+":"+reversed+":"+siding.getVehicleCars().stream().map(VehicleCar::getVehicleId).reduce("",(u,v)->u+"/"+v);
                if(!seen.add(key))continue;
                Parked preview=new Parked(extra,siding,data);preview.park(path.getEndDistance(),reversed);
                JsonObject plan=new JsonObject();plan.addProperty("platform",platform.getId());plan.addProperty("siding",siding.getId());plan.addProperty("label",(platform.area==null?"":platform.area.getName()+" · ")+platform.getName()+" · "+siding.getName());
                JsonArray cars=new JsonArray();int carIndex=0;plan.addProperty("total",siding.getVehicleCars().size());
                for(var car:preview.getVehicleCarsAndPositions()){
                    int index=carIndex++;if(car.right().isEmpty())continue;var anchor=car.right().get(0).left();if(Math.hypot(anchor.x-x,anchor.z-z)>130||Math.abs(anchor.y-y)>32)continue;
                    JsonObject entry=new JsonObject();entry.addProperty("index",index);entry.add("car",Utilities.getJsonObjectFromData(car.left()));JsonArray bogies=new JsonArray();
                    for(var bogie:car.right()){JsonArray points=new JsonArray();for(var v:new org.mtr.core.tool.Vector[]{bogie.left(),bogie.right()}){points.add(v.x);points.add(v.y);points.add(v.z);}bogies.add(points);}entry.add("bogies",bogies);cars.add(entry);
                }
                plan.add("cars",cars);if(plans.toString().length()+plan.toString().length()>27000)break;plans.add(plan);
            }
        }
        return new Response(plans.toString());
    }
}
