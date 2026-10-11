package org.mtr.mod.render;
import com.mojang.blaze3d.systems.RenderSystem;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import java.util.*;

/** Conservative per-mesh clip test before applying attributes; unknown/custom shader geometry fails open. */
public final class ModelPartVisibility {
    private record Bounds(float x0,float y0,float z0,float x1,float y1,float z1){}
    private static final Map<VertexArray,Bounds> BOUNDS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final org.joml.Matrix4f CLIP=new org.joml.Matrix4f();
    private static final org.joml.Vector4f CORNER=new org.joml.Vector4f();
    private ModelPartVisibility(){}
    public static void retain(VertexArray array,RawMesh mesh){
        float x0=Float.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Float.NEGATIVE_INFINITY,y1=x1,z1=x1;
        for(var vertex:mesh.vertices){var p=vertex.position;if(p==null)return;x0=Math.min(x0,p.getX());y0=Math.min(y0,p.getY());z0=Math.min(z0,p.getZ());x1=Math.max(x1,p.getX());y1=Math.max(y1,p.getY());z1=Math.max(z1,p.getZ());}
        if(Float.isFinite(x0+y0+z0+x1+y1+z1))BOUNDS.put(array,new Bounds(x0-16,y0-16,z0-16,x1+16,y1+16,z1+16));
    }
    public static boolean visible(VertexArray array,VertexAttributeState attributes){
        if(!Utilities.canUseCustomShader() || attributes.matrix4f==null || attributes.position!=null || "false".equalsIgnoreCase(System.getProperty("mtr.next.partVisibility","true")))return true;
        Bounds b=BOUNDS.get(array);if(b==null)return true;
        CLIP.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(attributes.matrix4f.data);
        int common=63;
        for(int i=0;i<8;i++){
            CORNER.set((i&1)==0?b.x0:b.x1,(i&2)==0?b.y0:b.y1,(i&4)==0?b.z0:b.z1,1).mul(CLIP);
            float x=CORNER.x,y=CORNER.y,z=CORNER.z,w=CORNER.w;if(!Float.isFinite(x+y+z+w))return true;
            int outside=(x < -w?1:0)|(x>w?2:0)|(y < -w?4:0)|(y>w?8:0)|(z < -w?16:0)|(z>w?32:0);common&=outside;
        }return common==0;
    }
    public static void remove(VertexArray array){BOUNDS.remove(array);}
}
