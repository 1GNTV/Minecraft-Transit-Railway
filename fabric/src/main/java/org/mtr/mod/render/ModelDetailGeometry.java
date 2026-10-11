package org.mtr.mod.render;
import org.mtr.mapping.render.model.RawMesh;
import java.util.*;

/** Remove only whole disconnected sub-resolution details. Retained triangles/attributes are exact. */
public final class ModelDetailGeometry {
    private record Position(float x,float y,float z){}
    private ModelDetailGeometry(){}
    public static int[] indices(RawMesh raw,double minimumExtent){
        if(raw.materialProperties.translucent || raw.materialProperties.cutoutHack || !raw.materialProperties.writeDepthBuf || raw.vertices.size()>200_000 || raw.faces.size()<16 || raw.faces.size()>200_000)return null;
        int n=raw.vertices.size();int[] parent=new int[n];for(int i=0;i<n;i++)parent[i]=i;
        Map<Position,Integer> positions=new HashMap<>();
        for(int i=0;i<n;i++){
            var p=raw.vertices.get(i).position;if(p==null || !Float.isFinite(p.getX()+p.getY()+p.getZ()))return null;
            Integer other=positions.putIfAbsent(new Position(p.getX()==0?0:p.getX(),p.getY()==0?0:p.getY(),p.getZ()==0?0:p.getZ()),i);if(other!=null)join(parent,i,other);
        }
        for(var face:raw.faces){if(face.vertices.length!=3)return null;for(int i:face.vertices)if(i<0 || i>=n)return null;join(parent,face.vertices[0],face.vertices[1]);join(parent,face.vertices[0],face.vertices[2]);}
        float[][] bounds=new float[n][];
        for(int i=0;i<n;i++){
            int root=find(parent,i);var p=raw.vertices.get(i).position;
            if(bounds[root]==null)bounds[root]=new float[]{p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()};
            else{var b=bounds[root];b[0]=Math.min(b[0],p.getX());b[1]=Math.min(b[1],p.getY());b[2]=Math.min(b[2],p.getZ());b[3]=Math.max(b[3],p.getX());b[4]=Math.max(b[4],p.getY());b[5]=Math.max(b[5],p.getZ());}
        }
        int[] result=new int[raw.faces.size()*3];int used=0;
        for(var face:raw.faces){var b=bounds[find(parent,face.vertices[0])];if(Math.max(b[3]-b[0],Math.max(b[4]-b[1],b[5]-b[2]))>=minimumExtent){System.arraycopy(face.vertices,0,result,used,3);used+=3;}}
        // Keep original if everything would disappear, or if reduction is too small to justify residency.
        return used==0 || used>result.length*0.9?null:Arrays.copyOf(result,used);
    }
    private static int find(int[] p,int i){while(p[i]!=i){p[i]=p[p[i]];i=p[i];}return i;}
    private static void join(int[] p,int a,int b){a=find(p,a);b=find(p,b);if(a!=b)p[Math.max(a,b)]=Math.min(a,b);}
}
