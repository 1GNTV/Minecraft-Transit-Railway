package org.mtr.mod.render;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
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
    private static long networkInvalidations,networkPartialInvalidations;
    private static final boolean DELTA_INVALIDATION=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railDelta", "true"));
    private static final RailColumnDelta RAIL_CHANGES=new RailColumnDelta(
        Math.max(32, Math.min(8192, Integer.getInteger("mtr.engine.maxDirtyRailColumns", 1024))));
    private static long railLookupHits,railLookupMisses,lastMetricsNanos;
    private static final Segment LOOKUP=new Segment();
    private static final int MAX_PREPARED_POSES=Math.max(8192,Math.min(131072,Integer.getInteger("mtr.engine.railPoses",32768)));
    private static final java.util.Map<Segment,Prepared> PREPARED=new java.util.LinkedHashMap<>(512,.75f,true);
    // One immutable render submission plan per visible rail/resource/direction.
    // Cached plan replays skip the segment callback, pose HashMap lookup,
    // trig computations and per-sample BlockPos creation on stable views.
    private static final boolean RETAIN_RAIL_PLANS=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railPlans","true"));
    private static final RailRenderPlanCache<RailPiece[]> RAIL_PLANS=new RailRenderPlanCache<>();
    private static Object planWorld;
    // Camera-independent scene plan: retain prepared rail pieces across views.
    private static final int SCENE_MAX=512, PIECE_MAX=65536, TRACK_MAX=4096;
    private static int scenePieces;
    private static final Map<SceneKey,StaticScene> STATIC_SCENES=new LinkedHashMap<>(64,.75f,true);
    private static final class RailSector {
        final int x,z;
        final ArrayList<RailPiece> pieces=new ArrayList<>();
        RailSector(int x,int z){this.x=x;this.z=z;}
    }
    private static final class StaticScene {
        final RailPiece[] pieces;
        final java.util.List<RailSector> sectors;
        StaticScene(RailPiece[] pieces){
            this.pieces=pieces;
            Map<Long,RailSector> indexed=new LinkedHashMap<>();
            for(RailPiece piece:pieces){
                int sx=(int)Math.floor(piece.sampleX/32D);
                int sz=(int)Math.floor(piece.sampleZ/32D);
                long id=((long)sx<<32)|(sz&0xffffffffL);
                indexed.computeIfAbsent(id,unused->new RailSector(sx,sz)).pieces.add(piece);
            }
            sectors=java.util.List.copyOf(indexed.values());
        }
    }
    private static final class SceneKey {
        final RailMath rail;final RailResource resource;final boolean flip;final int hash;
        SceneKey(RailMath rail,RailResource resource,boolean flip){
            this.rail=rail;this.resource=resource;this.flip=flip;
            hash=31*(31*System.identityHashCode(rail)+System.identityHashCode(resource))+Boolean.hashCode(flip);
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            return other instanceof SceneKey k && rail==k.rail && resource==k.resource && flip==k.flip;
        }
    }
    private static void clearStaticScenes(){STATIC_SCENES.clear();scenePieces=0;}
    private static final class RailPiece {
        private final Prepared prepared;
        private final BlockPos lightPosition;
        private StaticRegionRenderer.ReplayToken ticket;
        private int cachedLight=Integer.MIN_VALUE;
        final double sampleX,sampleY,sampleZ;
        RailPiece(Prepared prepared,BlockPos lightPosition){
            this(prepared,lightPosition,Double.NaN,Double.NaN,Double.NaN);
        }
        RailPiece(Prepared prepared,BlockPos lightPosition,double x,double y,double z){
            this.prepared=prepared;this.lightPosition=lightPosition;
            sampleX=x;sampleY=y;sampleZ=z;
        }
        Prepared prepared(){return prepared;}
        BlockPos lightPosition(){return lightPosition;}
    }
    private static long previousPlanHits, previousPlanBuilds;

    /**
     * Stable location identity shared by the pose cache, scene plan lookup and
     * compiled region selection. Record auto-hash re-traversed all seven fields
     * on every main/shadow pass (and once more for each nested region key).
     * Keep record-compatible equals semantics, but memoize its immutable hash.
     */
    private static final class Location {
        final RailResource resource;
        final double x,y,z;
        final float yaw,pitch,roll;
        private final int hash;
        Location(RailResource resource,double x,double y,double z,float yaw,float pitch,float roll){
            this.resource=resource;this.x=x;this.y=y;this.z=z;
            this.yaw=yaw;this.pitch=pitch;this.roll=roll;
            int h=java.util.Objects.hashCode(resource);
            h=31*h+Double.hashCode(x);h=31*h+Double.hashCode(y);h=31*h+Double.hashCode(z);
            h=31*h+Float.hashCode(yaw);h=31*h+Float.hashCode(pitch);h=31*h+Float.hashCode(roll);
            hash=h;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            if(!(other instanceof Location l))return false;
            return java.util.Objects.equals(resource,l.resource)
                && Double.compare(x,l.x)==0 && Double.compare(y,l.y)==0 && Double.compare(z,l.z)==0
                && Float.compare(yaw,l.yaw)==0 && Float.compare(pitch,l.pitch)==0
                && Float.compare(roll,l.roll)==0;
        }
    }
    private RailSectorBatcher(){}
    public static void beginFrame(){
        MinecraftClientData data=MinecraftClientData.getInstance();
        long revision=data.networkRevision;
        if(revision==appliedNetworkRevision)return;
        // Static sync is infrequent: scan the updated rail registry only on
        // revision changes, never every frame or shader pass.
        Map<String,RailColumnDelta.RailState> current=new HashMap<>(data.railWrapperList.size());
        for(MinecraftClientData.RailWrapper wrapper:data.railWrapperList.values()){
            Rail rail=wrapper.getRail();
            int signature=31*rail.getStyles().hashCode()+System.identityHashCode(rail.railMath);
            current.put(wrapper.hexId,new RailColumnDelta.RailState(
                rail,signature,rail.railMath.minX,rail.railMath.maxX,rail.railMath.minZ,rail.railMath.maxZ));
        }
        RailColumnDelta.Change changed=RAIL_CHANGES.update(current);
        if(!DELTA_INVALIDATION || changed.full()){
            PREPARED.clear();
            REGIONS.clear();
            ClassicRailGpuCache.clear();
            networkInvalidations++;
        }else if(!changed.columns().isEmpty()){
            // Pose entries may contain obsolete rail/resource shapes: cheap CPU
            // reset, but retain all GPU pages outside the dirty columns.
            PREPARED.clear();
            REGIONS.invalidateColumns(changed.columns());
            ClassicRailGpuCache.invalidateColumns(changed.columns());
            networkPartialInvalidations++;
        }
        RAIL_PLANS.clear();
        clearStaticScenes();
        ClassicRailSceneCache.clear();
        appliedNetworkRevision=revision;
    }
    public static boolean queue(RailResource resource,StoredMatrixTransformations fallback,double x,double y,double z,float yaw,float pitch,float roll,int light){
        var matrix=new org.joml.Matrix4f().rotateY(yaw).rotateX(pitch).rotateZ((float)Math.toRadians(roll));
        return REGIONS.queue(resource,fallback,new Location(resource,x,y,z,yaw,pitch,roll),x,y,z,matrix,light);
    }
    /** Source of truth for segment pose construction. Only plan misses call it. */
    private static Prepared prepare(RailResource resource,double x1,double z1,double x3,double z3,
                                    double y1,double y2,boolean flip){
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
        return prepared;
    }

    /**
     * Reuse a truly prepared visible rail across normal and shadow passes.
     * Every pass still resamples live block lighting and queues precisely the
     * currently visible set; only immutable geometry/pose work is retained.
     * Returns false if a budget/fallback forces the original sample callbacks.
     */
    public static boolean submitVisible(RailMath railMath,RailResource resource,double[] visible,
                                        boolean flip,ClientWorld clientWorld){
        if(!RETAIN_RAIL_PLANS || visible==null)return false;
        if(planWorld!=clientWorld.data){RAIL_PLANS.clear();planWorld=clientWorld.data;}
        RailPiece[] pieces=RAIL_PLANS.resolve(railMath,resource,visible,flip,samples->{
            RailPiece[] built=new RailPiece[samples.length/10];
            for(int n=0;n<built.length;n++){
                int i=n*10;
                Prepared prepared=prepare(resource,samples[i],samples[i+1],samples[i+4],samples[i+5],
                                          samples[i+8],samples[i+9],flip);
                BlockPos lightPos=Init.newBlockPos(samples[i],samples[i+8]+0.1,samples[i+1]);
                built[n]=new RailPiece(prepared,lightPos);
            }
            return built;
        });
        if(pieces==null)return false;
        // One model validation per rail, not per segment. Replay tickets own
        // immutable ScenePlan instances; dynamic light still samples each pass.
        var currentModel=REGIONS.modelForReplay(resource);
        for(RailPiece piece:pieces)submitPiece(piece,resource,clientWorld,currentModel);
        return true;
    }

    /** Shared submission: original region GPU page, live lighting, safe fallback. */
    private static void submitPiece(RailPiece piece,RailResource resource,ClientWorld world,
            org.mtr.mod.resource.OptimizedModelWrapper model){
        Prepared prep=piece.prepared();Location loc=prep.location();
        int light=RailLightFrameCache.get(world,piece.lightPosition());
        if(piece.cachedLight==light && REGIONS.replay(piece.ticket,world.data,model,light))return;
        if(REGIONS.queue(resource,prep.transform(),loc,
                loc.x,loc.y,loc.z,prep.matrix(),light)){
            piece.ticket=REGIONS.capture(loc,loc.x,loc.y,loc.z);piece.cachedLight=light;
        }else{
            piece.ticket=null;piece.cachedLight=Integer.MIN_VALUE;
            resource.render(prep.transform(),light);
        }
    }

    /** Immutable full-track plan survives camera movement; -1 means old path. */
    public static int submitStaticScene(RailMath rail,RailResource resource,boolean flip,ClientWorld world,
            double ex,double ey,double ez,int radius,double nx,double ny,double nz){
        if(!SillonMegaEngine.RAILS || !Double.isFinite(ex+ey+ez+nx+ny+nz))return -1;
        if(planWorld!=world.data){RAIL_PLANS.clear();clearStaticScenes();planWorld=world.data;}
        SceneKey key=new SceneKey(rail,resource,flip);
        StaticScene scene=STATIC_SCENES.get(key);
        if(scene==null){
            ArrayList<RailPiece> full=new ArrayList<>();
            final boolean[] overflow={false};
            MinecraftClientData.getInstance().railGeometryCache.render(rail,
                (x1,z1,x2,z2,x3,z3,x4,z4,y1,y2)->{
                    if(full.size()>=TRACK_MAX){overflow[0]=true;return;}
                    Prepared p=prepare(resource,x1,z1,x3,z3,y1,y2,flip);
                    full.add(new RailPiece(p,Init.newBlockPos(x1,y1+0.1,z1),x1,y1,z1));
                },resource.getRepeatInterval(),0,0);
            if(overflow[0])return -1;
            RailPiece[] pieces=full.toArray(new RailPiece[0]);
            scene=new StaticScene(pieces);
            STATIC_SCENES.put(key,scene);scenePieces+=pieces.length;
            Iterator<Map.Entry<SceneKey,StaticScene>> it=STATIC_SCENES.entrySet().iterator();
            while(STATIC_SCENES.size()>SCENE_MAX || scenePieces>PIECE_MAX){
                Map.Entry<SceneKey,StaticScene> old=it.next();
                scenePieces-=old.getValue().pieces.length;it.remove();
            }
        }
        final double radius2=(double)radius*radius;int visible=0;
        final var model=REGIONS.modelForReplay(resource);
        // Camera motion only visits sectors intersecting the view radius.
        // Each segment belongs to exactly one sector: no duplicate submission.
        for(RailSector sector:scene.sectors){
            double x=sector.x*32D,z=sector.z*32D;
            double closestX=Math.max(x,Math.min(ex,x+32D));
            double closestZ=Math.max(z,Math.min(ez,z+32D));
            if((closestX-ex)*(closestX-ex)+(closestZ-ez)*(closestZ-ez)>radius2)continue;
            for(RailPiece piece:sector.pieces){
                double dx=piece.sampleX-ex,dz=piece.sampleZ-ez,squared=dx*dx+dz*dz;
                if(squared<=radius2 && (squared<1024
                        || dx*nx+(piece.sampleY-ey)*ny+dz*nz>-0.00001)){
                    submitPiece(piece,resource,world,model);visible++;
                }
            }
        }
        return visible;
    }

    /** Original compatibility path: addon/editors/oversized rail caches. */
    public static void submit(RailResource resource,double x1,double z1,double x3,double z3,
                              double y1,double y2,boolean flip,int light){
        Prepared prepared=prepare(resource,x1,z1,x3,z3,y1,y2,flip);
        Location location=prepared.location();
        if(!REGIONS.queue(resource,prepared.transform(),location,location.x,location.y,location.z,prepared.matrix(),light))
            resource.render(prepared.transform(),light);
    }
    public static void flush(){
        REGIONS.flush();
        StaticDecorationRegions.flush();
        if(ENGINE_METRICS){
            long now=System.nanoTime();
            if(now-lastMetricsNanos>=10_000_000_000L){
                Init.LOGGER.info("MTR Engine rail pose lookup: hits={}, misses={}, cached-poses={}, full-network-invalidations={}, targeted-invalidations={}",
                    railLookupHits,railLookupMisses,PREPARED.size(),networkInvalidations,networkPartialInvalidations);
                railLookupHits=railLookupMisses=0;
                Init.LOGGER.info("MTR Engine retained rail plans: hits={}, builds={}, live-plans={}, enabled={}",
                    RAIL_PLANS.hits()-previousPlanHits,RAIL_PLANS.builds()-previousPlanBuilds,RAIL_PLANS.size(),RETAIN_RAIL_PLANS);
                previousPlanHits=RAIL_PLANS.hits();previousPlanBuilds=RAIL_PLANS.builds();
                lastMetricsNanos=now;
            }
        }
    }
    public static void clear(){RAIL_PLANS.clear();clearStaticScenes();ClassicRailSceneCache.clear();planWorld=null;PREPARED.clear();REGIONS.clear();StaticDecorationRegions.clear();ClassicRailGpuCache.clear();RAIL_CHANGES.clear();appliedNetworkRevision=Long.MIN_VALUE;}
    public static void reload(){clear();GpuDrivenRailCommands.clear();ModelDetailCache.clear();RailGeometrySnapshots.clear();}
}
