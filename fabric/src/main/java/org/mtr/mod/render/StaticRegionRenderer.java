package org.mtr.mod.render;
import org.mtr.mapping.holder.Matrix4f;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.holder.Vector3d;
import java.util.function.BiConsumer;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.*;
import org.mtr.mapping.render.vertex.*;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.mixin.OptimizedModelLayoutAccessor;
import org.mtr.mod.resource.*;
import java.util.*;

/** Incremental immutable pages for solid rails/decorations; selected spans retain fine visibility. */
public final class StaticRegionRenderer {
    private static final boolean CACHE_SCENE_PLANS = !"false".equalsIgnoreCase(System.getProperty("mtr.engine.scenePlans", "true"));
    private static final boolean CACHE_VISIBILITY_MASKS = !"false".equalsIgnoreCase(System.getProperty("mtr.engine.visibilityMasks", "true"));
    private static final boolean ENGINE_METRICS = Boolean.getBoolean("mtr.engine.metrics");
    // Reuse immutable model/snapshot metadata across main and Oculus shadow passes
    // within the same frame. Never pin a model for more than one render tick.
    private record ResourceSnapshot(OptimizedModelWrapper model, RailGeometrySnapshots.Snapshot snapshot){}
    private static final IdentityHashMap<StoredModelResourceBase,ResourceSnapshot> FRAME_RESOURCES = new IdentityHashMap<>();
    public static void beginFrame(){FRAME_RESOURCES.clear();}
    private record Sector(int x,int y,int z){}
    private static final class MatrixKey {
        final float[] values;final int hash;
        MatrixKey(org.joml.Matrix4f matrix){values=new float[16];matrix.get(values);hash=Arrays.hashCode(values);}
        // Compare local-space translation from primitives: creating a JOML
        // Matrix4f per rail per frame is pointless when nothing moved.
        boolean matchesLocal(org.joml.Matrix4f m,float x,float y,float z){
            return Float.compare(values[0],m.m00())==0 && Float.compare(values[1],m.m01())==0
                && Float.compare(values[2],m.m02())==0 && Float.compare(values[3],m.m03())==0
                && Float.compare(values[4],m.m10())==0 && Float.compare(values[5],m.m11())==0
                && Float.compare(values[6],m.m12())==0 && Float.compare(values[7],m.m13())==0
                && Float.compare(values[8],m.m20())==0 && Float.compare(values[9],m.m21())==0
                && Float.compare(values[10],m.m22())==0 && Float.compare(values[11],m.m23())==0
                && Float.compare(values[12],x)==0 && Float.compare(values[13],y)==0
                && Float.compare(values[14],z)==0 && Float.compare(values[15],m.m33())==0;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof MatrixKey key && Arrays.equals(values,key.values);}
    }
    /** Immutable region signature: hashing must not recurse through the rail
     * location, matrix and fragment on every compiled-page membership lookup. */
    private static final class Selection {
        private final OptimizedModelWrapper model;
        private final Object location;
        private final MatrixKey matrix;
        private final int light,chunk,hash;
        Selection(OptimizedModelWrapper model,Object location,MatrixKey matrix,int light,int chunk){
            this.model=model;this.location=location;this.matrix=matrix;this.light=light;this.chunk=chunk;
            int h=Objects.hashCode(model);
            h=31*h+Objects.hashCode(location);h=31*h+Objects.hashCode(matrix);
            h=31*h+light;hash=31*h+chunk;
        }
        OptimizedModelWrapper model(){return model;}
        Object location(){return location;}
        MatrixKey matrix(){return matrix;}
        int light(){return light;}
        int chunk(){return chunk;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            if(!(other instanceof Selection s))return false;
            return model==s.model && Objects.equals(location,s.location)
                && Objects.equals(matrix,s.matrix) && light==s.light && chunk==s.chunk;
        }
    }
    private static final class Fragment {
        private final Object location;
        private final OptimizedModelWrapper model;
        private final int chunk,hash;
        Fragment(Object location,OptimizedModelWrapper model,int chunk){
            this.location=location;this.model=model;this.chunk=chunk;
            int h=Objects.hashCode(location);
            h=31*h+Objects.hashCode(model);hash=31*h+chunk;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            if(!(other instanceof Fragment f))return false;
            return Objects.equals(location,f.location) && model==f.model && chunk==f.chunk;
        }
    }
    private record PageKey(Sector sector,int page){}
    // Immutable spatial scene plans survive frames; transient resources never enter this cache.
    private static final class PlanKey {
        private final Sector sector;
        private final Object location;
        private final int hash;
        PlanKey(Sector sector,Object location){
            this.sector=sector;this.location=location;
            hash=31*sector.hashCode()+Objects.hashCode(location);
        }
        Sector sector(){return sector;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            return other instanceof PlanKey k && sector.equals(k.sector)
                && Objects.equals(location,k.location);
        }
    }
    private record PlanRevision(OptimizedModelWrapper model,RailGeometrySnapshots.Snapshot snapshot,MatrixKey matrix,int light){}
    private record PlacedChunk(Selection selection,PageKey page){}
    private record ScenePlan(List<PlacedChunk> chunks){}
    private record Instance(ScenePlan plan,RailGeometrySnapshots.Snapshot snapshot,StoredModelResourceBase resource,StoredMatrixTransformations fallback,int light){}
    /**
     * Real persistent render queue ticket, captured only after a successful
     * scene-plan resolution. A replay bypasses queue(), scene-key lookup and
     * per-segment model/matrix validation. No GL buffers are owned here.
     */
    public static final class ReplayToken {
        private final Sector sector;
        private final int denseId;
        private final Instance instance;
        private final OptimizedModelWrapper model;
        private final long generation;
        private ReplayToken(Sector sector,int denseId,Instance instance,OptimizedModelWrapper model,long generation){
            this.sector=sector;this.denseId=denseId;this.instance=instance;
            this.model=model;this.generation=generation;
        }
    }

    private record MaterialSpan(MaterialProperties material,RegionVertexRanges.Span span){}
    private static final class Page implements AutoCloseable{
        final Map<MaterialProperties,VertexArray> arrays=new LinkedHashMap<>();final List<Mesh> meshes=new ArrayList<>();
        final Map<Selection,List<MaterialSpan>> spans=new HashMap<>();OptimizedModelWrapper model;
        // Render state and model are stable for this GPU page. The callback
        // reads the current pass and camera offset when invoked.
        BiConsumer<GraphicsHolder,Vector3d> drawCommand;
        private List<Selection> lastVisible=List.of();
        private Map<MaterialProperties,List<RegionVertexRanges.Span>> lastRanges=Map.of();
        private Map<MaterialProperties,List<RegionVertexRanges.Span>> submittedRanges;
        // Only visibility changes require regrouping index spans; camera-stable pages reuse
        // the immutable material mask instead of allocating new hash maps each frame.
        Map<MaterialProperties,List<RegionVertexRanges.Span>> ranges(List<Selection> visible){
            if(CACHE_VISIBILITY_MASKS && lastVisible.equals(visible))return lastRanges;
            Map<MaterialProperties,List<RegionVertexRanges.Span>> result=new HashMap<>();
            for(Selection selection:visible){
                List<MaterialSpan> contributions=spans.get(selection);
                if(contributions==null)throw new IllegalStateException("Region selection lacks compiled spans");
                for(MaterialSpan span:contributions)result.computeIfAbsent(span.material,k->new ArrayList<>()).add(span.span);
            }
            lastVisible=List.copyOf(visible);
            lastRanges=result;
            return result;
        }
        void activate(List<Selection> visible,Sector sector,boolean gpuCull){
            Map<MaterialProperties,List<RegionVertexRanges.Span>> ranges=ranges(visible);
            if(SillonMegaEngine.SCENE && submittedRanges==ranges)return;
            arrays.forEach((material,array)->{
                RegionVertexRanges.select(array,ranges.getOrDefault(material,List.of()));
                if(gpuCull)RegionVertexRanges.worldOrigin(array,sector.x()*16F,sector.y()*16F,sector.z()*16F);
            });
            submittedRanges=ranges;
        }
        @Override public void close(){arrays.values().forEach(a->{RegionVertexRanges.remove(a);a.close();});meshes.forEach(Mesh::close);lastVisible=List.of();lastRanges=Map.of();submittedRanges=null;}
    }
    // Dense stations can retain many region pages; the previous 64-page /
    // 256k-vertex cap evicted visible geometry repeatedly, causing fresh uploads.
    private static final int MAX_REGION_PAGES=Math.max(64,Math.min(512,Integer.getInteger("mtr.engine.regionPages",SillonMegaEngine.SCENE?384:256)));
    private static final int MAX_REGION_VERTICES=Math.max(256_000,Math.min(4_000_000,Integer.getInteger("mtr.engine.regionVertices",SillonMegaEngine.SCENE?1_500_000:1_000_000)));
    private static final int MAX_SCENE_LOCATIONS=Math.max(8192,Math.min(131072,Integer.getInteger("mtr.engine.sceneLocations",32768)));
    private final PersistentRegionCache<PageKey,Selection,Object,Page> cache=new PersistentRegionCache<>(MAX_REGION_PAGES,MAX_REGION_VERTICES,16_000);
    private final Map<Sector,Int2ObjectLinkedOpenHashMap<Instance>> groups=new LinkedHashMap<>();
    // Dense location IDs are assigned on cache-miss/queue only. A replay
    // uses the immutable int carried by its token, never Location.hashCode().
    // All equal Location objects map to the same ID (legacy dedup semantics).
    private static final boolean HELIX_DENSE_RAILS =
        !"false".equalsIgnoreCase(System.getProperty("mtr.helix.denseRails","true"));
    private final HashMap<Object,Integer> denseIds=new HashMap<>();
    private int nextDenseId=1;
    private int idFor(Object location){
        Integer existing=denseIds.get(location);
        if(existing!=null)return existing;
        int id=nextDenseId++;
        denseIds.put(location,id);
        return id;
    }
    private int cachedId(Object location){
        Integer id = denseIds.get(location);
        return id == null ? 0 : id;
    }
    // Reuse sector membership maps across the main + shadow passes.
    // Only the most recent sector bypasses group-map lookups. Ordered
    // location semantics and the original addon fallback remain unchanged.
    private static final boolean HELIX_STAGED_GROUPS =
        !"false".equalsIgnoreCase(System.getProperty("mtr.helix.stagedGroups","true"));
    private final ArrayDeque<Int2ObjectLinkedOpenHashMap<Instance>> freeGroups = new ArrayDeque<>();
    private Sector hotSector;
    private Int2ObjectLinkedOpenHashMap<Instance> hotGroup;
    private Int2ObjectLinkedOpenHashMap<Instance> groupFor(Sector sector) {
        if(HELIX_STAGED_GROUPS && hotGroup != null && sector.equals(hotSector)) return hotGroup;
        Int2ObjectLinkedOpenHashMap<Instance> result=groups.get(sector);
        if(result==null) {
            result=HELIX_STAGED_GROUPS ? freeGroups.pollFirst() : null;
            if(result==null) result=new Int2ObjectLinkedOpenHashMap<>();
            groups.put(sector,result);
        }
        hotSector=sector;hotGroup=result;
        return result;
    }
    private void releaseGroups(){
        for(Int2ObjectLinkedOpenHashMap<Instance> map : groups.values()) {
            map.clear();
            if(HELIX_STAGED_GROUPS && freeGroups.size()<128) freeGroups.addLast(map);
        }
        groups.clear();
        hotSector=null;hotGroup=null;
    }
    // Reuse the temporary per-pass staging maps/lists. No references to these
    // scratch lists survive inside compiled GPU pages or persistent scene plans.
    private final Map<PageKey,List<Selection>> wantedScratch=new LinkedHashMap<>();
    // Recycle the per-sector plan list instead of allocating one ArrayList for
    // every Oculus cascade and main pass. resolve() copies plans on misses;
    // on hits the temporary list is never retained.
    private final ArrayList<ScenePlan> sectorPlanScratch=new ArrayList<>();
    private static final boolean STRATA_IMMUTABLE_MASK=!"false".equalsIgnoreCase(System.getProperty("mtr.strata.immutableMasks","true"));
    private static final boolean CACHE_SECTOR_LISTS=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.sectorLists","true"));
    private final SectorRenderPlanCache<Sector,ScenePlan,Map<PageKey,List<Selection>>> sectorLists=
        new SectorRenderPlanCache<>(512);
    private long previousSectorHits,previousSectorBuilds;

    private final Map<PageKey,Page> readyScratch=new HashMap<>();
    private final Map<PageKey,List<Selection>> drawingScratch=new LinkedHashMap<>();
    private final ArrayDeque<ArrayList<Selection>> listPool=new ArrayDeque<>();
    private List<Selection> scratchList() {
        ArrayList<Selection> reuse=listPool.pollFirst();
        return reuse==null?new ArrayList<>():reuse;
    }
    private void recycle(Map<PageKey,List<Selection>> values) {
        for(List<Selection> list:values.values()){
            list.clear();
            if(listPool.size()<256)listPool.addLast((ArrayList<Selection>)list);
        }
        values.clear();
    }
    private final Map<Sector,RegionPageLayout<Fragment>> layouts=new HashMap<>();
    private int layoutLocations; // Incrementally maintained; no global layout traversal each frame.
    private final ScenePlanCache<PlanKey,PlanRevision,ScenePlan> scenePlans=new ScenePlanCache<>(MAX_SCENE_LOCATIONS);
    // Rail poses retain immutable Location instances for their whole lifetime.
    // Reuse the same PlanKey across main and shadow passes, rather than
    // allocating a new key and recomputing its nested location hash each time.
    // Decorations can receive mutable locations; those keep the ordinary path.
    private final IdentityHashMap<Object,PlanKey> railPlanKeys=new IdentityHashMap<>();
    private PlanKey planKey(Sector sector,Object location){
        if(family!=RenderPipelineMetrics.Family.RAIL)return new PlanKey(sector,location);
        PlanKey existing=railPlanKeys.get(location);
        if(existing!=null && existing.sector().equals(sector))return existing;
        PlanKey created=new PlanKey(sector,location);
        if(railPlanKeys.size()>=MAX_SCENE_LOCATIONS)railPlanKeys.clear();
        railPlanKeys.put(location,created);
        return created;
    }
    private static final boolean PERSISTENT_QUEUE=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.persistentQueue","true"));
    private static final boolean RAIL_LOD_PAGES=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railLod","true"));
    private long generation=1, queueCalls, replayCalls, previousQueues, previousReplays;
    private final RenderPipelineMetrics.Family family;
    private long lastMetricsNanos, previousHits, previousMisses, previousCompiledHits, previousBuilds, previousEvictions;
    private Object world;
    public StaticRegionRenderer(RenderPipelineMetrics.Family family){this.family=family;}

    /** Resolve the resource model ONCE per styled rail, not per segment. */
    public OptimizedModelWrapper modelForReplay(StoredModelResourceBase resource){
        if(!PERSISTENT_QUEUE)return null;
        return resource.getOptimizedModel();
    }

    public ReplayToken capture(Object location,double x,double y,double z){
        if(!PERSISTENT_QUEUE)return null;
        Sector sector=new Sector((int)Math.floor(x/16),(int)Math.floor(y/16),(int)Math.floor(z/16));
        Int2ObjectLinkedOpenHashMap<Instance> slot=groups.get(sector);
        int denseId = cachedId(location);
        Instance instance=slot==null?null:slot.get(denseId);
        if(instance==null)return null;
        ResourceSnapshot snapshot=FRAME_RESOURCES.get(instance.resource());
        if(snapshot==null || snapshot.snapshot()!=instance.snapshot())return null;
        return new ReplayToken(sector,denseId,instance,snapshot.model(),generation);
    }

    /**
     * True if and only if the original full scene submission was skipped.
     * All changed lighting, model/resource generations, network edits, world
     * swaps and unsupported rendering modes force queue() fallback.
     */
    public boolean replay(ReplayToken ticket,Object currentWorld,OptimizedModelWrapper model,int light){
        if(!PERSISTENT_QUEUE || !HELIX_DENSE_RAILS || ticket==null || currentWorld!=world
                || ticket.generation!=generation || ticket.model!=model
                || ticket.instance.light()!=light
                || !RailGeometrySnapshots.ENABLED || !OptimizedRenderer.hasOptimizedRendering()
                || GlStateTracker.isGl4ES())return false;
        // The instance and scene plan are immutable. Only the per-pass group
        // membership is registered; queue(), hashes and scene resolution vanish.
        groupFor(ticket.sector).put(ticket.denseId,ticket.instance);
        replayCalls++;
        return true;
    }

    public boolean queue(StoredModelResourceBase resource,StoredMatrixTransformations fallback,Object location,double x,double y,double z,org.joml.Matrix4f matrix,int light){
        queueCalls++;
        if(!RailGeometrySnapshots.ENABLED || !OptimizedRenderer.hasOptimizedRendering() || GlStateTracker.isGl4ES())return false;
        Object current=MinecraftClient.getInstance().getWorldMapped().data;if(current!=world){clear();world=current;}
        ResourceSnapshot cachedResource=FRAME_RESOURCES.get(resource);
        if(cachedResource==null){
            OptimizedModelWrapper resolved=resource.getOptimizedModel();
            if(resolved==null || resolved.getModel()==null)return false;
            RailGeometrySnapshots.Snapshot geometry=RailGeometrySnapshots.get(resolved.getModel());
            if(geometry==null)return false;
            cachedResource=new ResourceSnapshot(resolved,geometry);
            FRAME_RESOURCES.put(resource,cachedResource);
        }
        OptimizedModelWrapper model=cachedResource.model();
        RailGeometrySnapshots.Snapshot snapshot=cachedResource.snapshot();
        var sector=new Sector((int)Math.floor(x/16),(int)Math.floor(y/16),(int)Math.floor(z/16));
        float localX=(float)(x-sector.x*16D),localY=(float)(y-sector.y*16D),localZ=(float)(z-sector.z*16D);
        java.util.function.Function<PlanRevision,ScenePlan> buildPlan=revision->{
            RegionPageLayout<Fragment> layout=layouts.computeIfAbsent(sector,k->new RegionPageLayout<>(RailGeometrySnapshots.PAGE_VERTICES));
            int before=layout.locations();
            List<PlacedChunk> parts=new ArrayList<>(snapshot.chunks().size());
            try {
                for(int n=0;n<snapshot.chunks().size();n++){
                    Fragment fragment=new Fragment(location,model,n);
                    Selection selection=new Selection(model,fragment,revision.matrix(),light,n);
                    PageKey page=new PageKey(sector,layout.page(fragment,snapshot.chunks().get(n).vertices()));
                    parts.add(new PlacedChunk(selection,page));
                }
                return new ScenePlan(List.copyOf(parts));
            } finally {
                // Count newly registered fragments even if a later chunk fails.
                layoutLocations+=layout.locations()-before;
            }
        };
        ScenePlan plan=CACHE_SCENE_PLANS
            ? scenePlans.resolveLazy(planKey(sector,location),
                previous->previous.model()==model && previous.snapshot()==snapshot
                    && previous.light()==light && previous.matrix().matchesLocal(matrix,localX,localY,localZ),
                ()->new PlanRevision(model,snapshot,new MatrixKey(localMatrix(matrix,localX,localY,localZ)),light),
                buildPlan)
            : buildPlan.apply(new PlanRevision(model,snapshot,new MatrixKey(localMatrix(matrix,localX,localY,localZ)),light));
        groupFor(sector).put(idFor(location),new Instance(plan,snapshot,resource,fallback,light));return true;
    }
    private static org.joml.Matrix4f localMatrix(org.joml.Matrix4f source,float x,float y,float z){
        // Called on plan cache misses only; never on a stable queue hit.
        org.joml.Matrix4f matrix=new org.joml.Matrix4f(source);
        return matrix.m30(x).m31(y).m32(z);
    }
    public void flush(){
        cache.beginFrame();
        try {
            // Region page IDs and scene plans must be invalidated together.
            if(layoutLocations>MAX_SCENE_LOCATIONS){
                // Plans already queued this pass still refer to old page IDs.
                // Never reuse them after resetting the page layout: render the
                // complete original models for this one pass instead.
                generation++;cache.close();layouts.clear();scenePlans.clear();sectorLists.clear();railPlanKeys.clear();denseIds.clear();layoutLocations=0;
                for(var region:groups.values())for(Instance instance:region.values())
                    instance.resource().render(instance.fallback(),instance.light());
                return;
            }
            Map<PageKey,List<Selection>> wanted=wantedScratch;
            if(CACHE_SECTOR_LISTS){
                // The plan identity includes model, matrix, light and page
                // membership. Compare only one identity per model rather than
                // re-enumerating hundreds of PlacedChunks every shader pass.
                for(var region:groups.entrySet()){
                    sectorPlanScratch.clear();
                    for(Instance instance:region.getValue().values())sectorPlanScratch.add(instance.plan());
                    final List<ScenePlan> plans=sectorPlanScratch;
                    Map<PageKey,List<Selection>> pages=sectorLists.resolve(region.getKey(),plans,()->{
                        Map<PageKey,List<Selection>> built=new LinkedHashMap<>();
                        for(ScenePlan plan:plans)for(PlacedChunk part:plan.chunks())
                            built.computeIfAbsent(part.page(),k->new ArrayList<>()).add(part.selection());
                        Map<PageKey,List<Selection>> stable=new LinkedHashMap<>();
                        built.forEach((key,items)->stable.put(key,List.copyOf(items)));
                        return Collections.unmodifiableMap(stable);
                    });
                    // PageKey embeds sector: pages from different sectors
                    // cannot collide, so no mutable merged lists are needed.
                    wanted.putAll(pages);
                }
            }else{
                for(var region:groups.values())for(Instance instance:region.values())
                    for(PlacedChunk part:instance.plan().chunks())
                        wanted.computeIfAbsent(part.page(),k->scratchList()).add(part.selection());
            }

            Map<PageKey,Page> ready=readyScratch;
            wanted.forEach((key,selected)->{
                try {
                    Page page=cache.get(key,selected,Selection::location,
                        selection->{var snapshot=RailGeometrySnapshots.get(selection.model().getModel());
                            return snapshot==null?16_001:snapshot.chunks().get(selection.chunk()).vertices();},
                        this::build,CACHE_SECTOR_LISTS && STRATA_IMMUTABLE_MASK);
                    if(page!=null)ready.put(key,page);
                }catch(RuntimeException error){Init.LOGGER.error("MTR region page retained original rendering",error);}
            });
            // In a stable compiled frame EVERY needed page is ready. Drawing
            // then equals the immutable wanted membership exactly: rebuilding
            // the drawing lists per model would be wasted work. On any missing
            // page retain the original atomic per-object fallback below.
            boolean everyPageReady=ready.size()==wanted.size();
            Map<PageKey,List<Selection>> drawing=everyPageReady?wanted:drawingScratch;
            for(var region:groups.values())for(Instance instance:region.values()){
                if(!everyPageReady){
                    List<PlacedChunk> parts=instance.plan().chunks();
                    boolean allReady=true;
                    for(PlacedChunk part:parts)if(!ready.containsKey(part.page())){allReady=false;break;}
                    // Atomic per-object decision: never mix partial GPU mesh with fallback.
                    if(!allReady){instance.resource().render(instance.fallback(),instance.light());continue;}
                    for(PlacedChunk part:parts)
                        drawing.computeIfAbsent(part.page(),k->scratchList()).add(part.selection());
                }
                OptimizedModelWrapper remainder=instance.snapshot().remainder();
                if(remainder!=null)MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{
                    instance.fallback().transform(holder,offset);
                    try{CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(remainder,holder,instance.light(),family);}
                    finally{holder.pop();}
                });
            }
            drawing.forEach((key,selected)->{
                Page page=ready.get(key);
                page.activate(selected,key.sector(),family==RenderPipelineMetrics.Family.RAIL);
                if(page.drawCommand==null) page.drawCommand=pageDrawCommand(page,key.sector());
                MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,page.drawCommand);
            });
        } finally {
            releaseGroups();
            // Retained sector lists are IMMUTABLE; never clear/recycle them.
            if(CACHE_SECTOR_LISTS)wantedScratch.clear();
            else recycle(wantedScratch);
            recycle(drawingScratch);
            readyScratch.clear();
            if(ENGINE_METRICS){
                long now=System.nanoTime();
                if(now-lastMetricsNanos>=10_000_000_000L){
                    Init.LOGGER.info("MTR Engine scene: family={}, cached-plans={}, plan-hits={}, plan-builds={}, enabled={}",
                        family,scenePlans.size(),scenePlans.hits()-previousHits,scenePlans.misses()-previousMisses,CACHE_SCENE_PLANS);
                    previousHits=scenePlans.hits();previousMisses=scenePlans.misses();
                    Init.LOGGER.info("MTR Persistent queue: family={}, real-queue-calls={}, replayed-without-queue={}, enabled={}",
                        family,queueCalls-previousQueues,replayCalls-previousReplays,PERSISTENT_QUEUE);
                    previousQueues=queueCalls;previousReplays=replayCalls;

                    Init.LOGGER.info("MTR Engine retained sector lists: family={}, hits={}, builds={}, resident-sectors={}, enabled={}",
                        family,sectorLists.hits()-previousSectorHits,sectorLists.builds()-previousSectorBuilds,
                        sectorLists.size(),CACHE_SECTOR_LISTS);
                    previousSectorHits=sectorLists.hits();previousSectorBuilds=sectorLists.builds();
                    Init.LOGGER.info("MTR Engine regions: family={}, compiled-hits={}, page-builds={}, evictions={}, resident-vertices={}",
                        family,cache.compiledHits()-previousCompiledHits,cache.pageBuilds()-previousBuilds,
                        cache.pageEvictions()-previousEvictions,cache.residentVertices());
                    previousCompiledHits=cache.compiledHits();previousBuilds=cache.pageBuilds();
                    previousEvictions=cache.pageEvictions();
                    lastMetricsNanos=now;
                }
            }
        }
    }

    /** One safe render command per retained GPU page, rather than one Java
     * callback allocation per shadow cascade and main pass. */
    private BiConsumer<GraphicsHolder,Vector3d> pageDrawCommand(Page page,Sector sector) {
        return (holder,offset)->{
                    holder.push();
                    try{
                        holder.translate(sector.x()*16D-offset.getXMapped(),sector.y()*16D-offset.getYMapped(),sector.z()*16D-offset.getZMapped());
                        if(RAIL_LOD_PAGES && family==RenderPipelineMetrics.Family.RAIL){
                            var camera=MinecraftClient.getInstance().getGameRendererMapped().getCamera().getPos();
                            double dx=sector.x()*16D+8-camera.getXMapped();
                            double dy=sector.y()*16D+8-camera.getYMapped();
                            double dz=sector.z()*16D+8-camera.getZMapped();
                            double before=ModelDetailCache.pushDistance(dx*dx+dy*dy+dz*dz);
                            try{CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(page.model,holder,0,family);}
                            finally{ModelDetailCache.restoreDistance(before);}
                        }else{
                            CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(page.model,holder,0,family);
                        }
                    }finally{holder.pop();}
        };
    }

    private Page build(List<Selection> content){
        Page page=new Page();Map<MaterialProperties,RawMesh> combined=new LinkedHashMap<>();
        for(Selection s:content){
            Matrix4f transform=new Matrix4f(new org.joml.Matrix4f().set(s.matrix.values));
            var snapshot=RailGeometrySnapshots.get(s.model.getModel());if(snapshot==null)return null;
            List<MaterialSpan> spans=new ArrayList<>();
            for(RawMesh source:snapshot.chunks().get(s.chunk).meshes()){
                RawMesh baked=RailBatchGeometry.copy(source);baked.applyMatrix(transform);int light=Utilities.exchangeLightmapUVBits(s.light);baked.vertices.forEach(v->v.light=light);
                RawMesh target=combined.computeIfAbsent(source.materialProperties,RawMesh::new);
                // Bounds are baked in the same sector-local space as the
                // uploaded vertices. Compute command culling can therefore
                // use conservative per-submesh AABBs without CPU readback.
                float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
                float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
                for(var vertex:baked.vertices){
                    if(vertex.position==null)continue;
                    float x=vertex.position.getX(),y=vertex.position.getY(),z=vertex.position.getZ();
                    minX=Math.min(minX,x); minY=Math.min(minY,y); minZ=Math.min(minZ,z);
                    maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); maxZ=Math.max(maxZ,z);
                }
                spans.add(new MaterialSpan(source.materialProperties,
                    new RegionVertexRanges.Span(target.faces.size()*3,baked.faces.size()*3,
                        minX,minY,minZ,maxX,maxY,maxZ)));
                target.append(baked);
            }page.spans.put(s,spans);
        }
        var builder=new VertexAttributeMapping.Builder();OptimizedModelLayoutAccessor.mtr$getDefaultMapping().sources.forEach(builder::set);
        var mapping=builder.set(VertexAttributeType.UV_LIGHTMAP,VertexAttributeSource.VERTEX_BUFFER).build();
        GlStateTracker.capture();
        try{
            // Keep segment index ranges stable. Do not apply global triangle reordering here.
            for(var entry:combined.entrySet()){
                Mesh mesh=FastRegionMeshUploader.upload(entry.getValue(),mapping);
                page.meshes.add(mesh);
                if(RAIL_LOD_PAGES && family==RenderPipelineMetrics.Family.RAIL){
                    // Static region pages are immutable and opaque: retain a
                    // bounded CPU snapshot for the existing background LOD
                    // simplifier, then attach to the uploaded vertex array.
                    ModelDetailCache.beginVehicleUpload();
                    try{
                        ModelDetailCache.uploaded(mesh,entry.getValue());
                        page.arrays.put(entry.getKey(),new VertexArray(mesh,mapping));
                    }finally{ModelDetailCache.endVehicleUpload();}
                }else page.arrays.put(entry.getKey(),new VertexArray(mesh,mapping));
            }
            page.model=OptimizedModelWrapper.fromUploadedModel(new OptimizedModel(new ArrayList<>(page.arrays.values())));return page;
        }catch(RuntimeException|Error error){page.close();throw error;}finally{GlStateTracker.restore();}
    }
    /**
     * Targeted static-world invalidation, called on the render thread between
     * passes. Keep unrelated GPU region pages and scene plans resident.
     * Sector coordinates are world block coordinates divided by 16 (floored).
     */
    public void invalidateSector(int x,int y,int z){
        generation++;
        Sector sector=new Sector(x,y,z);
        cache.invalidateWhere(page->page.sector().equals(sector));
        scenePlans.invalidateWhere(plan->plan.sector().equals(sector));
        sectorLists.invalidateWhere(s->s.equals(sector));
        RegionPageLayout<Fragment> discarded=layouts.remove(sector);
        if(discarded!=null)layoutLocations=Math.max(0,layoutLocations-discarded.locations());
        groups.remove(sector);
        denseIds.clear(); // generation invalidated all prior tokens
        hotSector=null;hotGroup=null;
    }

    /** Invalidate only the columns touched by edited rail geometry.
     * Geometry's rail model Y offset may cross a chunk-height boundary, so
     * every 16-block Y-sector in a changed X/Z column must be invalidated.
     * Active camera and shader-pass visibility never affect membership.
     */
    public void invalidateColumns(Set<Long> columns){
        if(columns.isEmpty())return;
        generation++;
        java.util.function.Predicate<Sector> dirty=sector->
            columns.contains((((long)sector.x())<<32) ^ (sector.z()&0xffffffffL));
        cache.invalidateWhere(page->dirty.test(page.sector()));
        scenePlans.invalidateWhere(plan->dirty.test(plan.sector()));
        sectorLists.invalidateWhere(dirty);
        var iterator=layouts.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(dirty.test(entry.getKey())){
                layoutLocations=Math.max(0,layoutLocations-entry.getValue().locations());
                iterator.remove();
            }
        }
        groups.keySet().removeIf(dirty);
        denseIds.clear(); // generation invalidated all prior tokens
        hotSector=null;hotGroup=null;
    }
    public void clear(){generation++;FRAME_RESOURCES.clear();cache.close();releaseGroups();freeGroups.clear();denseIds.clear();nextDenseId=1;layouts.clear();scenePlans.clear();sectorLists.clear();railPlanKeys.clear();if(CACHE_SECTOR_LISTS)wantedScratch.clear();else recycle(wantedScratch);recycle(drawingScratch);readyScratch.clear();listPool.clear();layoutLocations=0;world=null;previousHits=previousMisses=0;}
}
