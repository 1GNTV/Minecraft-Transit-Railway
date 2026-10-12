package org.mtr.mod.render;
import org.mtr.mapping.holder.Matrix4f;
import org.mtr.mapping.holder.MinecraftClient;
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
    private record Selection(OptimizedModelWrapper model,Object location,MatrixKey matrix,int light,int chunk){}
    private record Fragment(Object location,OptimizedModelWrapper model,int chunk){}
    private record PageKey(Sector sector,int page){}
    // Immutable spatial scene plans survive frames; transient resources never enter this cache.
    private record PlanKey(Sector sector,Object location){}
    private record PlanRevision(OptimizedModelWrapper model,RailGeometrySnapshots.Snapshot snapshot,MatrixKey matrix,int light){}
    private record PlacedChunk(Selection selection,PageKey page){}
    private record ScenePlan(List<PlacedChunk> chunks){}
    private record Instance(ScenePlan plan,RailGeometrySnapshots.Snapshot snapshot,StoredModelResourceBase resource,StoredMatrixTransformations fallback,int light){}
    private record MaterialSpan(MaterialProperties material,RegionVertexRanges.Span span){}
    private static final class Page implements AutoCloseable{
        final Map<MaterialProperties,VertexArray> arrays=new LinkedHashMap<>();final List<Mesh> meshes=new ArrayList<>();
        final Map<Selection,List<MaterialSpan>> spans=new HashMap<>();OptimizedModelWrapper model;
        private List<Selection> lastVisible=List.of();
        private Map<MaterialProperties,List<RegionVertexRanges.Span>> lastRanges=Map.of();
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
        @Override public void close(){arrays.values().forEach(a->{RegionVertexRanges.remove(a);a.close();});meshes.forEach(Mesh::close);lastVisible=List.of();lastRanges=Map.of();}
    }
    // Dense stations can retain many region pages; the previous 64-page /
    // 256k-vertex cap evicted visible geometry repeatedly, causing fresh uploads.
    private static final int MAX_REGION_PAGES=Math.max(64,Math.min(512,Integer.getInteger("mtr.engine.regionPages",256)));
    private static final int MAX_REGION_VERTICES=Math.max(256_000,Math.min(4_000_000,Integer.getInteger("mtr.engine.regionVertices",1_000_000)));
    private static final int MAX_SCENE_LOCATIONS=Math.max(8192,Math.min(131072,Integer.getInteger("mtr.engine.sceneLocations",32768)));
    private final PersistentRegionCache<PageKey,Selection,Object,Page> cache=new PersistentRegionCache<>(MAX_REGION_PAGES,MAX_REGION_VERTICES,16_000);
    private final Map<Sector,LinkedHashMap<Object,Instance>> groups=new LinkedHashMap<>();
    // Reuse the temporary per-pass staging maps/lists. No references to these
    // scratch lists survive inside compiled GPU pages or persistent scene plans.
    private final Map<PageKey,List<Selection>> wantedScratch=new LinkedHashMap<>();
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
    private final RenderPipelineMetrics.Family family;
    private long lastMetricsNanos, previousHits, previousMisses;
    private Object world;
    public StaticRegionRenderer(RenderPipelineMetrics.Family family){this.family=family;}
    public boolean queue(StoredModelResourceBase resource,StoredMatrixTransformations fallback,Object location,double x,double y,double z,org.joml.Matrix4f matrix,int light){
        if(!RailGeometrySnapshots.ENABLED || !OptimizedRenderer.hasOptimizedRendering() || GlStateTracker.isGl4ES())return false;
        Object current=MinecraftClient.getInstance().getWorldMapped().data;if(current!=world){clear();world=current;}
        OptimizedModelWrapper model=resource.getOptimizedModel();if(model==null || model.getModel()==null)return false;
        var snapshot=RailGeometrySnapshots.get(model.getModel());if(snapshot==null)return false;
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
            ? scenePlans.resolveLazy(new PlanKey(sector,location),
                previous->previous.model()==model && previous.snapshot()==snapshot
                    && previous.light()==light && previous.matrix().matchesLocal(matrix,localX,localY,localZ),
                ()->new PlanRevision(model,snapshot,new MatrixKey(localMatrix(matrix,localX,localY,localZ)),light),
                buildPlan)
            : buildPlan.apply(new PlanRevision(model,snapshot,new MatrixKey(localMatrix(matrix,localX,localY,localZ)),light));
        groups.computeIfAbsent(sector,k->new LinkedHashMap<>()).put(location,new Instance(plan,snapshot,resource,fallback,light));return true;
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
                cache.close();layouts.clear();scenePlans.clear();layoutLocations=0;
                for(var region:groups.values())for(Instance instance:region.values())
                    instance.resource().render(instance.fallback(),instance.light());
                return;
            }
            Map<PageKey,List<Selection>> wanted=wantedScratch;
            for(var region:groups.values())for(Instance instance:region.values())
                for(PlacedChunk part:instance.plan().chunks())
                    wanted.computeIfAbsent(part.page(),k->scratchList()).add(part.selection());

            Map<PageKey,Page> ready=readyScratch;
            wanted.forEach((key,selected)->{
                try {
                    Page page=cache.get(key,selected,Selection::location,
                        selection->{var snapshot=RailGeometrySnapshots.get(selection.model().getModel());
                            return snapshot==null?16_001:snapshot.chunks().get(selection.chunk()).vertices();},
                        this::build);
                    if(page!=null)ready.put(key,page);
                }catch(RuntimeException error){Init.LOGGER.error("MTR region page retained original rendering",error);}
            });
            Map<PageKey,List<Selection>> drawing=drawingScratch;
            for(var region:groups.values())for(Instance instance:region.values()){
                List<PlacedChunk> parts=instance.plan().chunks();
                boolean allReady=true;
                for(PlacedChunk part:parts)if(!ready.containsKey(part.page())){allReady=false;break;}
                // Atomic per-object decision: never draw a partial mesh plus its fallback.
                if(!allReady){instance.resource().render(instance.fallback(),instance.light());continue;}
                for(PlacedChunk part:parts)
                    drawing.computeIfAbsent(part.page(),k->scratchList()).add(part.selection());
                OptimizedModelWrapper remainder=instance.snapshot().remainder();
                if(remainder!=null)MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{
                    instance.fallback().transform(holder,offset);
                    try{CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(remainder,holder,instance.light(),family);}
                    finally{holder.pop();}
                });
            }
            drawing.forEach((key,selected)->{
                Page page=ready.get(key);
                Map<MaterialProperties,List<RegionVertexRanges.Span>> ranges=page.ranges(selected);
                page.arrays.forEach((material,array)->RegionVertexRanges.select(array,ranges.getOrDefault(material,List.of())));
                Sector sector=key.sector();
                MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{
                    holder.push();
                    try{
                        holder.translate(sector.x()*16D-offset.getXMapped(),sector.y()*16D-offset.getYMapped(),sector.z()*16D-offset.getZMapped());
                        CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(page.model,holder,0,family);
                    }finally{holder.pop();}
                });
            });
        } finally {
            groups.clear();
            recycle(wantedScratch);
            recycle(drawingScratch);
            readyScratch.clear();
            if(ENGINE_METRICS){
                long now=System.nanoTime();
                if(now-lastMetricsNanos>=10_000_000_000L){
                    Init.LOGGER.info("MTR Engine scene: family={}, cached-plans={}, plan-hits={}, plan-builds={}, enabled={}",
                        family,scenePlans.size(),scenePlans.hits()-previousHits,scenePlans.misses()-previousMisses,CACHE_SCENE_PLANS);
                    previousHits=scenePlans.hits();previousMisses=scenePlans.misses();
                    lastMetricsNanos=now;
                }
            }
        }
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
                spans.add(new MaterialSpan(source.materialProperties,new RegionVertexRanges.Span(target.faces.size()*3,baked.faces.size()*3)));target.append(baked);
            }page.spans.put(s,spans);
        }
        var builder=new VertexAttributeMapping.Builder();OptimizedModelLayoutAccessor.mtr$getDefaultMapping().sources.forEach(builder::set);
        var mapping=builder.set(VertexAttributeType.UV_LIGHTMAP,VertexAttributeSource.VERTEX_BUFFER).build();
        GlStateTracker.capture();
        try{
            // Keep segment index ranges stable. Do not apply global triangle reordering here.
            for(var entry:combined.entrySet()){Mesh mesh=FastRegionMeshUploader.upload(entry.getValue(),mapping);page.meshes.add(mesh);page.arrays.put(entry.getKey(),new VertexArray(mesh,mapping));}
            page.model=OptimizedModelWrapper.fromUploadedModel(new OptimizedModel(new ArrayList<>(page.arrays.values())));return page;
        }catch(RuntimeException|Error error){page.close();throw error;}finally{GlStateTracker.restore();}
    }
    public void clear(){cache.close();groups.clear();layouts.clear();scenePlans.clear();recycle(wantedScratch);recycle(drawingScratch);readyScratch.clear();listPool.clear();layoutLocations=0;world=null;previousHits=previousMisses=0;}
}
