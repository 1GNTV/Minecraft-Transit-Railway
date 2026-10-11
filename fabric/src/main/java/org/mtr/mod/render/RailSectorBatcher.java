package org.mtr.mod.render;
import org.mtr.mod.resource.RailResource;
/** Persistent solid rail regions, including Oculus; per-segment selection remains unchanged. */
public final class RailSectorBatcher {
    private static final StaticRegionRenderer REGIONS=new StaticRegionRenderer(RenderPipelineMetrics.Family.RAIL);
    private record Location(RailResource resource,double x,double y,double z,float yaw,float pitch,float roll){}
    private RailSectorBatcher(){}
    public static void beginFrame(){}
    public static boolean queue(RailResource resource,StoredMatrixTransformations fallback,double x,double y,double z,float yaw,float pitch,float roll,int light){
        var matrix=new org.joml.Matrix4f().rotateY(yaw).rotateX(pitch).rotateZ((float)Math.toRadians(roll));
        return REGIONS.queue(resource,fallback,new Location(resource,x,y,z,yaw,pitch,roll),x,y,z,matrix,light);
    }
    public static void flush(){REGIONS.flush();StaticDecorationRegions.flush();}
    public static void clear(){REGIONS.clear();StaticDecorationRegions.clear();}
    public static void reload(){clear();RailGeometrySnapshots.clear();}
}
