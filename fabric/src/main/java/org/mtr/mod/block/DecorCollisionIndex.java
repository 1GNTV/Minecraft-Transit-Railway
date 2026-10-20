package org.mtr.mod.block;
import org.mtr.mod.decor.*;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.mtr.mod.block.BlockStationDecor;
import java.lang.ref.WeakReference;
import java.util.*;

/** Loaded anchors indexed over their entire extent. No placeholder blocks, world scans or entity ticks. */
public final class DecorCollisionIndex {
    private record Cell(int x,int y,int z){}
    private record Entry(WeakReference<BlockStationDecor.BlockEntity> owner,AABB bounds,List<AABB> solids,List<Cell> cells){}
    private static final class Index {
        final Map<Long,Entry> anchors=new HashMap<>();
        final Map<Cell,Set<Long>> cells=new HashMap<>();
    }
    private static final Map<Level,Index> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    private DecorCollisionIndex(){}
    private static int bin(double v){return (int)Math.floor(v/8);}
    private static List<Cell> cells(AABB b){List<Cell> result=new ArrayList<>();for(int x=bin(b.minX);x<=bin(b.maxX);x++)for(int y=bin(b.minY);y<=bin(b.maxY);y++)for(int z=bin(b.minZ);z<=bin(b.maxZ);z++)result.add(new Cell(x,y,z));return result;}
    public static void remove(BlockStationDecor.BlockEntity entity){
        Level world=entity.getLevel();if(world==null)return;Index index=WORLDS.get(world);if(index==null)return;
        long key=entity.getBlockPos().asLong();Entry old=index.anchors.get(key);
        if(old==null||old.owner().get()!=entity)return;
        index.anchors.remove(key);for(Cell c:old.cells()){var bucket=index.cells.get(c);if(bucket!=null){bucket.remove(key);if(bucket.isEmpty())index.cells.remove(c);}}
    }
    public static void update(BlockStationDecor.BlockEntity entity){
        Level world=entity.getLevel();if(world==null||entity.spec()==null)return;
        remove(entity);if(entity.isRemoved())return;
        var s=entity.spec();var mesh=DecorMesh.get(s);
        double rotation=-org.mtr.mod.block.IBlock.getStatePropertySafe(entity.getCachedState2(),BlockStationDecor.FACING).asRotation()+s.yaw();
        double yaw=Math.toRadians(rotation),pitch=Math.toRadians(s.pitch());
        double ox=entity.getBlockPos().getX()+.5+s.x(),oy=entity.getBlockPos().getY()+s.y(),oz=entity.getBlockPos().getZ()+.5+s.z();
        List<AABB> boxes=new ArrayList<>();
        boolean rotated=Math.abs(rotation%90)>.001||Math.abs(s.pitch())>.001;
        for(var q:s.kind()==DecorKind.WAYPOINT?List.<DecorMesh.Solid>of():mesh.solids()){
            double dx=q.X()-q.x(),dy=q.Y()-q.y(),dz=q.Z()-q.z();
            int axis=dx>=dy&&dx>=dz?0:dy>=dz?1:2;
            int n=rotated?Math.min(80,Math.max(1,(int)Math.ceil(Math.max(dx,Math.max(dy,dz))/.2))):1;
            for(int k=0;k<n;k++){
                double t=(double)k/n,T=(double)(k+1)/n;
                boxes.add(transform(axis==0?q.x()+dx*t:q.x(),axis==1?q.y()+dy*t:q.y(),axis==2?q.z()+dz*t:q.z(),axis==0?q.x()+dx*T:q.X(),axis==1?q.y()+dy*T:q.Y(),axis==2?q.z()+dz*T:q.Z(),yaw,pitch,ox,oy,oz));
            }
        }
        AABB bounds=transform(mesh.minX,mesh.minY,mesh.minZ,mesh.maxX,mesh.maxY,mesh.maxZ,yaw,pitch,ox,oy,oz).inflate(.05);
        Entry entry=new Entry(new WeakReference<>(entity),bounds,List.copyOf(boxes),cells(bounds));
        Index index=WORLDS.computeIfAbsent(world,k->new Index());long key=entity.getBlockPos().asLong();index.anchors.put(key,entry);
        for(Cell c:entry.cells())index.cells.computeIfAbsent(c,k->new HashSet<>()).add(key);
    }
    private static AABB transform(double x,double y,double z,double X,double Y,double Z,double yaw,double pitch,double ox,double oy,double oz){
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int i=0;i<8;i++){double a=(i&1)==0?x:X,b=(i&2)==0?y:Y,c=(i&4)==0?z:Z;
            double yy=b*Math.cos(pitch)-c*Math.sin(pitch),zz=b*Math.sin(pitch)+c*Math.cos(pitch);
            double xx=a*Math.cos(yaw)+zz*Math.sin(yaw)+ox;zz=-a*Math.sin(yaw)+zz*Math.cos(yaw)+oz;yy+=oy;
            minX=Math.min(minX,xx);minY=Math.min(minY,yy);minZ=Math.min(minZ,zz);maxX=Math.max(maxX,xx);maxY=Math.max(maxY,yy);maxZ=Math.max(maxZ,zz);
        }
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public static AABB bounds(BlockStationDecor.BlockEntity entity){
        var world=entity.getLevel();Index index=world==null?null:WORLDS.get(world);Entry entry=index==null?null:index.anchors.get(entity.getBlockPos().asLong());
        if(entry==null){update(entity);index=world==null?null:WORLDS.get(world);entry=index==null?null:index.anchors.get(entity.getBlockPos().asLong());}
        return entry==null?new AABB(entity.getBlockPos()):entry.bounds();
    }
    public static Iterable<VoxelShape> collisions(Level world,net.minecraft.world.entity.Entity entity,AABB query){
        // Keep the native context, iterator, chunk filtering and per-block shape hooks.
        Iterable<VoxelShape> vanilla=()->new net.minecraft.world.level.BlockCollisions<>(world,entity,query,false,(pos,shape)->shape);
        return append(world,query,vanilla);
    }
    public static Iterable<VoxelShape> append(Level world,AABB query,Iterable<VoxelShape> vanilla){
        Index index=WORLDS.get(world);if(index==null||index.anchors.isEmpty())return vanilla;
        Set<Long> keys=new HashSet<>();for(Cell cell:cells(query)){var bucket=index.cells.get(cell);if(bucket!=null)keys.addAll(bucket);}
        List<VoxelShape> extra=new ArrayList<>();
        for(long key:keys){Entry entry=index.anchors.get(key);if(entry==null||!entry.bounds().intersects(query))continue;var owner=entry.owner().get();if(owner==null||owner.isRemoved())continue;
            for(AABB box:entry.solids())if(box.intersects(query))extra.add(Shapes.create(box));
        }
        if(extra.isEmpty())return vanilla;
        return ()->new Iterator<>() {final Iterator<VoxelShape> original=vanilla.iterator(),added=extra.iterator();public boolean hasNext(){return original.hasNext()||added.hasNext();}public VoxelShape next(){return original.hasNext()?original.next():added.next();}};
    }
}
