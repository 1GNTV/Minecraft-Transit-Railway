package org.mtr.mod.render;

import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.vertex.Vertex;
import java.util.*;

/** Endpoint edge collapse with locked borders/seams, topology, winding and affine-UV checks.
 * No vertex buffer is changed. Radius accumulation bounds all represented source positions.
 */
public final class BoundedMeshSimplifier {
    private record Point(float x,float y,float z) {}
    private record Edge(int a,int b,double distance) {}
    private BoundedMeshSimplifier() {}
    public static int[] simplify(RawMesh raw,double error) {
        int count=raw.vertices.size();
        if(count<4 || count>20_000 || raw.faces.size()>40_000 || raw.materialProperties.translucent || raw.materialProperties.cutoutHack || !raw.materialProperties.writeDepthBuf || !Double.isFinite(error) || error<=0)return null;
        int[][] faces=new int[raw.faces.size()][];List<Set<Integer>> incident=new ArrayList<>();
        for(int i=0;i<count;i++){Vertex v=raw.vertices.get(i);if(v.position==null || !Float.isFinite(v.position.getX()+v.position.getY()+v.position.getZ()) || !Float.isFinite(v.u+v.v))return null;incident.add(new HashSet<>());}
        Map<Long,Integer> edgeCounts=new HashMap<>();
        for(int f=0;f<faces.length;f++){int[] indices=raw.faces.get(f).vertices;if(indices.length!=3)return null;faces[f]=indices.clone();for(int index:indices){if(index<0 || index>=count)return null;incident.get(index).add(f);}for(int e=0;e<3;e++)edgeCounts.merge(edge(indices[e],indices[(e+1)%3]),1,Integer::sum);}
        boolean[] locked=new boolean[count];
        edgeCounts.forEach((key,uses)->{if(uses!=2){locked[(int)(key>>>32)]=true;locked[(int)(long)key]=true;}});
        // Coincident but distinct vertices can carry a UV or normal seam. Keep both endpoints.
        Map<Point,Integer> positions=new HashMap<>();
        for(int i=0;i<count;i++){var p=raw.vertices.get(i).position;Integer old=positions.putIfAbsent(new Point(p.getX()==0?0:p.getX(),p.getY()==0?0:p.getY(),p.getZ()==0?0:p.getZ()),i);if(old!=null){locked[i]=true;locked[old]=true;}}
        List<Edge> candidates=new ArrayList<>();edgeCounts.forEach((key,uses)->{int a=(int)(key>>>32),b=(int)(long)key;double distance=distance(raw.vertices.get(a),raw.vertices.get(b));if(uses==2 && distance<=error)candidates.add(new Edge(a,b,distance));});
        candidates.sort(Comparator.comparingDouble(Edge::distance).thenComparingInt(Edge::a).thenComparingInt(Edge::b));
        int[] parent=new int[count];double[] radius=new double[count];for(int i=0;i<count;i++)parent[i]=i;
        for(Edge candidate:candidates){if(Thread.currentThread().isInterrupted())return null;int a=root(parent,candidate.a),b=root(parent,candidate.b);if(a==b || locked[a] || locked[b])continue;
            double distance=distance(raw.vertices.get(a),raw.vertices.get(b));
            int from=radius[a]<=radius[b]?a:b,to=from==a?b:a;
            if(radius[from]+distance>error)continue;
            Vertex source=raw.vertices.get(from),target=raw.vertices.get(to);
            if(source.color!=target.color || source.light!=target.light || !sameNormal(source,target))continue;
            Set<Integer> neighborsA=neighbors(faces,incident.get(from),from),neighborsB=neighbors(faces,incident.get(to),to);neighborsA.retainAll(neighborsB);
            Set<Integer> shared=new HashSet<>(incident.get(from));shared.retainAll(incident.get(to));Set<Integer> opposite=new HashSet<>();
            if(shared.size()!=2)continue;for(int f:shared)for(int v:faces[f])if(v!=from && v!=to)opposite.add(v);
            if(!neighborsA.equals(opposite))continue; // link condition: no holes or non-manifold vertices
            boolean safe=true;
            double[] reference=null;
            for(int f:incident.get(from)){double[] gradient=basis(raw,faces[f]);if(gradient==null){safe=false;break;}if(reference==null)reference=gradient;else for(int j=3;j<9;j++)if(Math.abs(reference[j]-gradient[j])>1e-4*Math.max(1,Math.abs(reference[j])))safe=false;}
            if(!safe)continue;
            for(int f:incident.get(from)){int[] old=faces[f];if(shared.contains(f))continue;int[] next=old.clone();for(int j=0;j<3;j++)if(next[j]==from)next[j]=to;if(!preservesFace(raw,old,next)){safe=false;break;}}
            if(!safe)continue;
            for(int f:new ArrayList<>(incident.get(from))){int[] triangle=faces[f];if(shared.contains(f)){for(int v:triangle)incident.get(v).remove(f);faces[f]=null;continue;}for(int j=0;j<3;j++)if(triangle[j]==from)triangle[j]=to;incident.get(to).add(f);}
            incident.get(from).clear();parent[from]=to;radius[to]=Math.max(radius[to],radius[from]+distance);
        }
        int[] result=new int[faces.length*3];int used=0;for(int[] face:faces)if(face!=null){System.arraycopy(face,0,result,used,3);used+=3;}
        return used==0 || used>result.length*.9?null:Arrays.copyOf(result,used);
    }
    private static Set<Integer> neighbors(int[][] faces,Set<Integer> incident,int vertex){Set<Integer> result=new HashSet<>();for(int f:incident)if(faces[f]!=null)for(int v:faces[f])if(v!=vertex)result.add(v);return result;}
    private static int root(int[] parents,int i){while(parents[i]!=i){parents[i]=parents[parents[i]];i=parents[i];}return i;}
    private static long edge(int a,int b){return ((long)Math.min(a,b)<<32)|(Math.max(a,b)&0xffffffffL);}
    private static double distance(Vertex a,Vertex b){double x=a.position.getX()-b.position.getX(),y=a.position.getY()-b.position.getY(),z=a.position.getZ()-b.position.getZ();return Math.sqrt(x*x+y*y+z*z);}
    private static boolean sameNormal(Vertex a,Vertex b){if(a.normal==null || b.normal==null)return a.normal==b.normal;return Math.abs(a.normal.getX()-b.normal.getX())+Math.abs(a.normal.getY()-b.normal.getY())+Math.abs(a.normal.getZ()-b.normal.getZ())<1e-5;}
    private static double[] vector(Vertex a,Vertex b){return new double[]{b.position.getX()-a.position.getX(),b.position.getY()-a.position.getY(),b.position.getZ()-a.position.getZ()};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double[] basis(RawMesh raw,int[] triangle){Vertex a=raw.vertices.get(triangle[0]),b=raw.vertices.get(triangle[1]),c=raw.vertices.get(triangle[2]);double[] x=vector(a,b),y=vector(a,c),normal=cross(x,y);double xx=dot(x,x),yy=dot(y,y),xy=dot(x,y),det=xx*yy-xy*xy;if(det<1e-20)return null;double[] result=new double[9];System.arraycopy(normal,0,result,0,3);for(int coord=0;coord<2;coord++){double bx=coord==0?b.u-a.u:b.v-a.v,cy=coord==0?c.u-a.u:c.v-a.v,alpha=(bx*yy-cy*xy)/det,beta=(cy*xx-bx*xy)/det;for(int j=0;j<3;j++)result[3+coord*3+j]=alpha*x[j]+beta*y[j];}return result;}
    private static boolean preservesFace(RawMesh raw,int[] old,int[] next){double[] before=basis(raw,old),after=basis(raw,next);if(before==null || after==null)return false;double cosine=(before[0]*after[0]+before[1]*after[1]+before[2]*after[2])/Math.sqrt((before[0]*before[0]+before[1]*before[1]+before[2]*before[2])*(after[0]*after[0]+after[1]*after[1]+after[2]*after[2]));if(cosine<.999999)return false;for(int i=3;i<9;i++)if(Math.abs(before[i]-after[i])>1e-4*Math.max(1,Math.abs(before[i])))return false;return true;}
}
