package org.mtr.mod.render;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.resource.ObjectResource;

/** Separate collection/residency per main/shadow pass; supports ordinary static eye-candy objects. */
public final class StaticDecorationRegions {
    private static final StaticRegionRenderer MAIN=new StaticRegionRenderer(RenderPipelineMetrics.Family.DECORATION),SHADOW=new StaticRegionRenderer(RenderPipelineMetrics.Family.DECORATION);
    private StaticDecorationRegions(){}
    public static boolean queue(ObjectResource resource,StoredMatrixTransformations fallback,Object location,double x,double y,double z,float facing,float rx,float ry,float rz,int light){
        var matrix=new org.joml.Matrix4f().rotateY((float)Math.toRadians(facing)).rotateX(rx+(float)Math.PI).rotateY(ry).rotateZ(rz);
        return (OptimizedRenderer.renderingShadows()?SHADOW:MAIN).queue(resource,fallback,location,x,y,z,matrix,light);
    }
    public static void flush(){(OptimizedRenderer.renderingShadows()?SHADOW:MAIN).flush();}
    public static void clear(){MAIN.clear();SHADOW.clear();}
}
