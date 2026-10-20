package org.mtr.mod.chantier;

import java.util.*;
import static org.mtr.mod.chantier.ChantierPlan.*;

/** Union first, watertight six-neighbour envelope second. Overlapping rooms never retain partition walls. */
public final class ChantierRaster {
    public record Cell(int x,int y,int z) {public Cell add(int x,int y,int z){return new Cell(this.x+x,this.y+y,this.z+z);}}
    public record Voxel(Cell cell,Role role) {}
    private static final int[][] NEIGHBOURS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final ChantierPlan plan;
    private final Set<Cell> inside=new HashSet<>();
    private final Map<Cell,Role> furniture=new HashMap<>();
    private final Set<Cell> stairClearance=new HashSet<>(),stairTreads=new HashSet<>();
    private final Map<Cell,Role> result=new HashMap<>();
    private int partIndex,x,y,z,minX,minY,minZ,maxX,maxY,maxZ;
    private Iterator<Cell> boundary;
    private boolean done;
    public ChantierRaster(ChantierPlan plan){this.plan=plan;beginPart();}
    private void beginPart(){Part p=plan.parts().get(partIndex);int margin=p.width()/2+3;minX=(int)Math.floor(Math.min(p.a().x(),p.b().x()))-margin;maxX=(int)Math.ceil(Math.max(p.a().x(),p.b().x()))+margin;minZ=(int)Math.floor(Math.min(p.a().z(),p.b().z()))-margin;maxZ=(int)Math.ceil(Math.max(p.a().z(),p.b().z()))+margin;minY=(int)Math.floor(Math.min(p.a().y(),p.b().y()))-(p.profile()==2||p.kind()==Kind.HALL?1:0);maxY=(int)Math.ceil(Math.max(p.a().y(),p.b().y()))+p.height();x=minX;y=minY;z=minZ;
        long tests=(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);if(tests>12000000)throw new IllegalArgumentException("Volume trop diagonal : fractionne le tracé.");}
    /** Bounded CPU work, also used by the client to avoid a preview freeze. */
    public boolean step(int budget){
        if(done)return true;
        while(budget-->0&&partIndex<plan.parts().size()){
            Part p=plan.parts().get(partIndex);classify(p,new Cell(x,y,z));
            if(++x>maxX){x=minX;if(++z>maxZ){z=minZ;if(++y>maxY){partIndex++;if(partIndex<plan.parts().size())beginPart();}}}
        }
        if(partIndex<plan.parts().size())return false;
        if(boundary==null)boundary=inside.iterator();
        while(budget-->0&&boundary.hasNext()){
            Cell c=boundary.next();result.put(c,stairTreads.contains(c)?Role.STAIR:stairClearance.contains(c)?Role.AIR:furniture.getOrDefault(c,Role.AIR));for(int[] d:NEIGHBOURS){Cell n=c.add(d[0],d[1],d[2]);if(!inside.contains(n)){
                Role r=d[1]<0?Role.FLOOR:d[1]>0?Role.ROOF:Role.WALL;
                if(r==Role.ROOF&&Math.floorMod(c.x()+c.z(),plan.spacing())==0)r=Role.LIGHT;
                result.putIfAbsent(n,r);
            }}if(result.size()>MAX_CELLS)throw new IllegalArgumentException("Chantier limité à "+MAX_CELLS+" blocs : réduis les volumes.");
        }
        done=!boundary.hasNext();return done;
    }
    private void classify(Part p,Cell cell){
        double dx=p.b().x()-p.a().x(),dz=p.b().z()-p.a().z(),len=p.length();double px=cell.x()+.5-p.a().x(),pz=cell.z()+.5-p.a().z();double along=(px*dx+pz*dz)/len;if(along<0||along>len)return;
        double across=(-px*dz+pz*dx)/len,half=p.width()/2.0;if(Math.abs(across)>half)return;double base=p.a().y()+(p.b().y()-p.a().y())*along/len;
        // Stair treads are horizontal one-block steps, rather than an unusable smooth ramp.
        base=Math.floor(base);if(p.kind()==Kind.STAIRS&&p.b().y()<p.a().y())base--;
        double h=cell.y()-base;if(p.kind()==Kind.HALL&&h==-1){furniture.put(cell,Role.FLOOR);return;}if(p.kind()==Kind.STAIRS&&along>=len-.001)return;if(h<(p.profile()==2?-1:0))return;
        double top=p.height();if(p.profile()==1)top=p.height()*.55+p.height()*.45*Math.sqrt(Math.max(0,1-across*across/(half*half)));else if(p.profile()==2){double centre=p.height()/2.0-1,radius=p.height()/2.0;double horizontal=across/half,vertical=(h+.5-centre)/radius;if(horizontal*horizontal+vertical*vertical>1)return;top=centre+radius*Math.sqrt(Math.max(0,1-horizontal*horizontal));}
        if(h>=top)return;inside.add(cell);if(inside.size()>MAX_CELLS)throw new IllegalArgumentException("Chantier trop grand.");
        if(p.kind()==Kind.STAIRS){stairClearance.add(cell);if(h<1)stairTreads.add(cell);}
        if(p.kind()==Kind.STATION&&h>=0&&h<1){double track=p.trackOffset();boolean onPlatform=p.layout()==0?Math.abs(across)>track+1.65:Math.abs(across)<track-1.65;if(onPlatform)furniture.put(cell,Role.PLATFORM);}
        boolean stationColumn=p.kind()==Kind.STATION&&h>=1&&(p.layout()==1?Math.abs(across)<.55:Math.abs(Math.abs(across)-(half-1.5))<.55);boolean hallColumn=p.kind()==Kind.HALL&&p.width()>=13&&h>=0&&Math.abs(across)<.55;if(plan.pillars()&&h<top&&(stationColumn||hallColumn)&&Math.floorMod((int)Math.floor(along),plan.spacing())==0)furniture.put(cell,Role.PILLAR);
    }
    public boolean done(){return done;}
    public Map<Cell,Role> cells(){if(!done)throw new IllegalStateException("Plan encore en calcul.");return Collections.unmodifiableMap(result);}
    public List<Voxel> ordered(){if(!done)throw new IllegalStateException();List<Voxel> values=new ArrayList<>(result.size());result.forEach((c,r)->values.add(new Voxel(c,r)));values.sort(Comparator.<Voxel>comparingInt(v->v.role()==Role.WALL||v.role()==Role.FLOOR||v.role()==Role.ROOF||v.role()==Role.LIGHT?0:v.role()==Role.AIR?1:2).thenComparingInt(v->-v.cell().y()).thenComparingInt(v->v.cell().x()).thenComparingInt(v->v.cell().z()));return values;}
    public static ChantierRaster complete(ChantierPlan p){ChantierRaster r=new ChantierRaster(p);while(!r.step(65536)){}return r;}
}
