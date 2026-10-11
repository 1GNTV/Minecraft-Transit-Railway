package org.mtr.mod.render;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.resource.VehicleResource;
import java.util.*;

/** Warm approaching consists before camera visibility; at most one cache step after each main frame. */
public final class PredictiveVehiclePreloader {
    private record Key(VehicleResource resource,int car,int total) {}
    private static final Map<Key,Long> PENDING=new LinkedHashMap<>();
    private static final Map<VehicleExtension,Double> DISTANCES=new WeakHashMap<>();
    private static long lastDrain;
    private PredictiveVehiclePreloader() {}
    public static void consider(VehicleExtension vehicle,ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,ObjectObjectImmutablePair<ObjectArrayList<PositionAndRotation>,PositionAndRotation>>> cars,Vector3d camera){
        if ("false".equalsIgnoreCase(System.getProperty("mtr.next.preload","true")) || org.mtr.mapping.mapper.OptimizedRenderer.renderingShadows() || cars.isEmpty()) return;
        double nearest=Double.POSITIVE_INFINITY;
        for(var car:cars){var p=car.right().right().position;double x=p.x-camera.getXMapped(),y=p.y-camera.getYMapped(),z=p.z-camera.getZMapped();nearest=Math.min(nearest,x*x+y*y+z*z);}
        Double previous=DISTANCES.put(vehicle,nearest);
        // Include nearby consists on the first observed frame; farther ones only when approaching.
        if (nearest>320*320 || nearest>192*192 && (previous==null || nearest>=previous)) return;
        for(int i=0;i<cars.size();i++){
            final int car=i;
            CustomResourceLoader.getVehicleById(vehicle.getTransportMode(),cars.get(i).left().getVehicleId(),details->{
                VehicleResource resource=details.left();resource.prepareModels(car,cars.size());
                Key key=new Key(resource,car,cars.size());
                if(PENDING.size()<64 || PENDING.containsKey(key))PENDING.put(key,System.currentTimeMillis());
            });
        }
    }
    public static void drain(){
        long now=System.currentTimeMillis(); if(now-lastDrain<16 || PENDING.isEmpty())return;lastDrain=now;
        var iterator=PENDING.entrySet().iterator();var entry=iterator.next();iterator.remove();Key key=entry.getKey();
        if(now-entry.getValue()>5000)return;
        // Never force nested model/upload caches: respect the existing global loading token.
        if(key.resource.getCachedVehicleResource(key.car,key.total,false)==null)PENDING.put(key,entry.getValue());
    }
    public static void clear(){PENDING.clear();DISTANCES.clear();lastDrain=0;}
}
