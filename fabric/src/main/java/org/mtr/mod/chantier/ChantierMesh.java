package org.mtr.mod.chantier;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.mtr.mod.chantier.ChantierPlan.*;
import static org.mtr.mod.chantier.ChantierRaster.*;
/** Greedy surface rectangles: a long wall costs one quad, not one draw per block. */
public final class ChantierMesh {
    public record Quad(int[] xyz,Role role){}
    private record Face(int axis,int sign,int plane,Role role){}
    private record UV(int u,int v){}
    public static List<Quad> build(Map<Cell,Role> cells,BooleanSupplier cancelled){
        Map<Face,TreeSet<UV>> planes=new HashMap<>();int checked=0;
        for(var entry:cells.entrySet()){
            if((checked++&1023)==0&&cancelled.getAsBoolean())return List.of();Role role=entry.getValue();if(role==Role.AIR)continue;Cell c=entry.getKey();int[] p={c.x(),c.y(),c.z()};
            for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){int[] q=p.clone();q[axis]+=sign;Role neighbour=cells.get(new Cell(q[0],q[1],q[2]));if(neighbour!=null&&neighbour!=Role.AIR)continue;
                Face face=new Face(axis,sign,p[axis]+(sign>0?1:0),role);planes.computeIfAbsent(face,f->new TreeSet<>(Comparator.comparingInt(UV::v).thenComparingInt(UV::u))).add(new UV(p[(axis+1)%3],p[(axis+2)%3]));}
        }
        List<Quad> result=new ArrayList<>();for(var entry:planes.entrySet()){Face f=entry.getKey();var pixels=entry.getValue();while(!pixels.isEmpty()){
            if(cancelled.getAsBoolean())return List.of();UV start=pixels.first();int w=1,h=1;while(pixels.contains(new UV(start.u+w,start.v)))w++;outer:while(true){for(int u=0;u<w;u++)if(!pixels.contains(new UV(start.u+u,start.v+h)))break outer;h++;}
            for(int v=0;v<h;v++)for(int u=0;u<w;u++)pixels.remove(new UV(start.u+u,start.v+v));int[] xyz=new int[12];int[][] corners={{0,0},{w,0},{w,h},{0,h}};for(int i=0;i<4;i++){xyz[i*3+f.axis]=f.plane;xyz[i*3+(f.axis+1)%3]=start.u+corners[i][0];xyz[i*3+(f.axis+2)%3]=start.v+corners[i][1];}result.add(new Quad(xyz,f.role));
        }}return List.copyOf(result);
    }
}
