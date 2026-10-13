package org.mtr.mod.render;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
/** Persistent solid rail regions, including Oculus; per-segment selection remains unchanged. */
public final class RailSectorBatcher {
    private static final StaticRegionRenderer REGIONS=new StaticRegionRenderer(RenderPipelineMetrics.Family.RAIL);
    /**
     * Query the rail pose cache without allocating a new record for every
     * stationary segment on every frame. The scratch key is never inserted.
     * Calls come from the main render thread, never from workers.
     */
    private static final class Segment {
        RailResource resource;
        double x1,z1,x3,z3,y1,y2;
        boolean flip;
        int hash;
        Segment set(RailResource resource,double x1,double z1,double x3,double z3,double y1,double y2,boolean flip){
            this.resource=resource;this.x1=x1;this.z1=z1;this.x3=x3;this.z3=z3;this.y1=y1;this.y2=y2;this.flip=flip;
            int h=resource==null?0:resource.hashCode();
            h=31*h+Double.hashCode(x1);h=31*h+Double.hashCode(z1);
            h=31*h+Double.hashCode(x3);h=31*h+Double.hashCode(z3);
            h=31*h+Double.hashCode(y1);h=31*h+Double.hashCode(y2);
            this.hash=31*h+Boolean.hashCode(flip);
            return this;
        }
        Segment immutableCopy(){return new Segment().set(resource,x1,z1,x3,z3,y1,y2,flip);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object o){
            if(!(o instanceof Segment s))return false;
            return java.util.Objects.equals(resource,s.resource)
                && Double.doubleToLongBits(x1)==Double.doubleToLongBits(s.x1)
                && Double.doubleToLongBits(z1)==Double.doubleToLongBits(s.z1)
                && Double.doubleToLongBits(x3)==Double.doubleToLongBits(s.x3)
                && Double.doubleToLongBits(z3)==Double.doubleToLongBits(s.z3)
                && Double.doubleToLongBits(y1)==Double.doubleToLongBits(s.y1)
                && Double.doubleToLongBits(y2)==Double.doubleToLongBits(s.y2) && flip==s.flip;
        }
    }
    private record Prepared(Location location,StoredMatrixTransformations transform,org.joml.Matrix4f matrix){}
    private static final boolean REUSE_KEYS=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railLookup","true"));
    private static final boolean ENGINE_METRICS=Boolean.getBoolean("mtr.engine.metrics");
    // Network sync may arrive between frames. GPU cleanup is deferred to this
    // render-thread entry point; never release GL buffers on the packet thread.
    private static long appliedNetworkRevision=Long.MIN_VALUE;
    private static long networkInvalidations;
    private static long railLookupHits,railLookupMisses,lastMetricsNanos;
    private static final Segment LOOKUP=new Segment();
    private static final int MAX_PREPARED_POSES=Math.max(8192,Math.min(131072,Integer.getInteger("mtr.engine.railPoses",32768)));
    private static final java.util.Map<Segment,Prepared> PREPARED=new java.util.LinkedHashMap<>(512,.75f,true);
    private record Location(RailResource resource,double x,double y,double z,float yaw,float pitch,float roll){}
    private RailSectorBatcher(){}
    public static void beginFrame(){
        long revision=MinecraftClientData.getInstance().networkRevision;
        if(revision!=appliedNetworkRevision){
            PREPARED.clear();
            REGIONS.clear();
            appliedNetworkRevision=revision;
            networkInvalidations++;
        }
    }
    public static boolean queue(RailResource resource,StoredMatrixTransformations fallback,double x,double y,double z,float yaw,float pitch,float roll,int light){
        var matrix=new org.joml.Matrix4f().rotateY(yaw).rotateX(pitch).rotateZ((float)Math.toRadians(roll));
        return REGIONS.queue(resource,fallback,new Location(resource,x,y,z,yaw,pitch,roll),x,y,z,matrix,light);
    }
    public static void submit(RailResource resource,double x1,double z1,double x3,double z3,double y1,double y2,boolean flip,int light){
        Segment lookup=REUSE_KEYS?LOOKUP.set(resource,x1,z1,x3,z3,y1,y2,flip)
                                  :new Segment().set(resource,x1,z1,x3,z3,y1,y2,flip);
        Prepared prepared=PREPARED.get(lookup);
        if(ENGINE_METRICS){if(prepared==null)railLookupMisses++;else railLookupHits++;}
        if(prepared==null){
            double dx=x3-x1,dz=z3-z1;
            float yaw=(float)(Math.PI/2-Math.atan2(dz,dx)+(flip?Math.PI:0));
            float pitch=(float)(Math.PI-Math.atan2(y2-y1,Math.sqrt(dx*dx+dz*dz))*(flip?-1:1));
            float roll=(float)((x1*z1)%10)/100;
            Location location=new Location(resource,(x1+x3)/2,(y1+y2)/2+resource.getModelYOffset(),(z1+z3)/2,yaw,pitch,roll);
            var transform=new StoredMatrixTransformations(location.x,location.y,location.z);
            transform.add(holder->{holder.rotateYRadians(yaw);holder.rotateXRadians(pitch);holder.rotateZDegrees(roll);});
            prepared=new Prepared(location,transform,new org.joml.Matrix4f().rotateY(yaw).rotateX(pitch).rotateZ((float)Math.toRadians(roll)));
            if(PREPARED.size()>=MAX_PREPARED_POSES){var iterator=PREPARED.keySet().iterator();iterator.next();iterator.remove();}
            PREPARED.put(lookup.immutableCopy(),prepared);
        }
        Location location=prepared.location;
        if(!REGIONS.queue(resource,prepared.transform,location,location.x,location.y,location.z,prepared.matrix,light))resource.render(prepared.transform,light);
    }
    public static void flush(){
        REGIONS.flush();
        StaticDecorationRegions.flush();
        if(ENGINE_METRICS){
            long now=System.nanoTime();
            if(now-lastMetricsNanos>=10_000_000_000L){
                Init.LOGGER.info("MTR Engine rail pose lookup: hits={}, misses={}, cached-poses={}, static-network-invalidations={}",
                    railLookupHits,railLookupMisses,PREPARED.size(),networkInvalidations);
                railLookupHits=railLookupMisses=0;
                lastMetricsNanos=now;
            }
        }
    }
    public static void clear(){PREPARED.clear();REGIONS.clear();StaticDecorationRegions.clear();appliedNetworkRevision=Long.MIN_VALUE;}
    public static void reload(){clear();ModelDetailCache.clear();RailGeometrySnapshots.clear();}
}
