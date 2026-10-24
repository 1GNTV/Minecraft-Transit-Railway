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
    // prepareAsync is idempotent, but walking the same model trees at 60 FPS
    // wastes render-thread time. Retry periodically so delayed resources still load.
    private static final long PREPARE_RETRY_MS=500;
    private static final int MAX_RECENT_PREPARES=256;
    private static final Map<Key,Long> RECENT_PREPARES=new LinkedHashMap<>(128,.75f,true);
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
        final int totalCars=cars.size();
        final long now=System.currentTimeMillis();
        for(int i=0;i<totalCars;i++){
            final int car=i;
            CustomResourceLoader.getVehicleById(vehicle.getTransportMode(),cars.get(i).left().getVehicleId(),details->{
                VehicleResource resource=details.left();
                Key key=new Key(resource,car,totalCars);
                Long previousPrepare=RECENT_PREPARES.get(key);
                if(previousPrepare!=null && now-previousPrepare>=0 && now-previousPrepare<PREPARE_RETRY_MS)return;
                resource.prepareModels(car,totalCars);
                RECENT_PREPARES.put(key,now);
                if(RECENT_PREPARES.size()>MAX_RECENT_PREPARES)
                    RECENT_PREPARES.remove(RECENT_PREPARES.keySet().iterator().next());
                if(PENDING.size()<64 || PENDING.containsKey(key))PENDING.put(key,now);
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
    public static void clear(){PENDING.clear();RECENT_PREPARES.clear();DISTANCES.clear();lastDrain=0;}
}
