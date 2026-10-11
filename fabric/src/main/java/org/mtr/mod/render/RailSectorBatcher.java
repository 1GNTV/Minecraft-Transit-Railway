package org.mtr.mod.render;
import org.mtr.mod.resource.RailResource;
/** Persistent solid rail regions, including Oculus; per-segment selection remains unchanged. */
public final class RailSectorBatcher {
    private static final StaticRegionRenderer REGIONS=new StaticRegionRenderer(RenderPipelineMetrics.Family.RAIL);
    private record Segment(RailResource resource,double x1,double z1,double x3,double z3,double y1,double y2,boolean flip){}
    private record Prepared(Location location,StoredMatrixTransformations transform,org.joml.Matrix4f matrix){}
    private static final java.util.Map<Segment,Prepared> PREPARED=new java.util.LinkedHashMap<>(512,.75f,true);
    private record Location(RailResource resource,double x,double y,double z,float yaw,float pitch,float roll){}
    private RailSectorBatcher(){}
    public static void beginFrame(){}
    public static boolean queue(RailResource resource,StoredMatrixTransformations fallback,double x,double y,double z,float yaw,float pitch,float roll,int light){
        var matrix=new org.joml.Matrix4f().rotateY(yaw).rotateX(pitch).rotateZ((float)Math.toRadians(roll));
        return REGIONS.queue(resource,fallback,new Location(resource,x,y,z,yaw,pitch,roll),x,y,z,matrix,light);
    }
    public static void submit(RailResource resource,double x1,double z1,double x3,double z3,double y1,double y2,boolean flip,int light){
        Segment key=new Segment(resource,x1,z1,x3,z3,y1,y2,flip);
        Prepared prepared=PREPARED.get(key);
        if(prepared==null){
            double dx=x3-x1,dz=z3-z1;
            float yaw=(float)(Math.PI/2-Math.atan2(dz,dx)+(flip?Math.PI:0));
            float pitch=(float)(Math.PI-Math.atan2(y2-y1,Math.sqrt(dx*dx+dz*dz))*(flip?-1:1));
            float roll=(float)((x1*z1)%10)/100;
            Location location=new Location(resource,(x1+x3)/2,(y1+y2)/2+resource.getModelYOffset(),(z1+z3)/2,yaw,pitch,roll);
            var transform=new StoredMatrixTransformations(location.x,location.y,location.z);
            transform.add(holder->{holder.rotateYRadians(yaw);holder.rotateXRadians(pitch);holder.rotateZDegrees(roll);});
            prepared=new Prepared(location,transform,new org.joml.Matrix4f().rotateY(yaw).rotateX(pitch).rotateZ((float)Math.toRadians(roll)));
            if(PREPARED.size()>=8192){var iterator=PREPARED.keySet().iterator();iterator.next();iterator.remove();}
            PREPARED.put(key,prepared);
        }
        Location location=prepared.location;
        if(!REGIONS.queue(resource,prepared.transform,location,location.x,location.y,location.z,prepared.matrix,light))resource.render(prepared.transform,light);
    }
    public static void flush(){REGIONS.flush();StaticDecorationRegions.flush();}
    public static void clear(){PREPARED.clear();REGIONS.clear();StaticDecorationRegions.clear();}
    public static void reload(){clear();ModelDetailCache.clear();RailGeometrySnapshots.clear();}
}
