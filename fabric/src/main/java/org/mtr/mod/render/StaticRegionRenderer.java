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
    private record Sector(int x,int y,int z){}
    private static final class MatrixKey {
        final float[] values;final int hash;
        MatrixKey(org.joml.Matrix4f matrix){values=new float[16];matrix.get(values);hash=Arrays.hashCode(values);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof MatrixKey key && Arrays.equals(values,key.values);}
    }
    private record Selection(OptimizedModelWrapper model,Object location,MatrixKey matrix,int light,int chunk){}
    private record Fragment(Object location,OptimizedModelWrapper model,int chunk){}
    private record PageKey(Sector sector,int page){}
    private record Instance(Selection selection,RailGeometrySnapshots.Snapshot snapshot,StoredModelResourceBase resource,StoredMatrixTransformations fallback){}
    private record MaterialSpan(MaterialProperties material,RegionVertexRanges.Span span){}
    private static final class Page implements AutoCloseable{
        final Map<MaterialProperties,VertexArray> arrays=new LinkedHashMap<>();final List<Mesh> meshes=new ArrayList<>();
        final Map<Selection,List<MaterialSpan>> spans=new HashMap<>();OptimizedModelWrapper model;
        @Override public void close(){arrays.values().forEach(a->{RegionVertexRanges.remove(a);a.close();});meshes.forEach(Mesh::close);}
    }
    private final PersistentRegionCache<PageKey,Selection,Object,Page> cache=new PersistentRegionCache<>(64,256_000,16_000);
    private final Map<Sector,LinkedHashMap<Object,Instance>> groups=new LinkedHashMap<>();
    private final Map<Sector,RegionPageLayout<Fragment>> layouts=new HashMap<>();
    private final RenderPipelineMetrics.Family family;
    private Object world;
    public StaticRegionRenderer(RenderPipelineMetrics.Family family){this.family=family;}
    public boolean queue(StoredModelResourceBase resource,StoredMatrixTransformations fallback,Object location,double x,double y,double z,org.joml.Matrix4f matrix,int light){
        if(!RailGeometrySnapshots.ENABLED || !OptimizedRenderer.hasOptimizedRendering() || GlStateTracker.isGl4ES())return false;
        Object current=MinecraftClient.getInstance().getWorldMapped().data;if(current!=world){clear();world=current;}
        OptimizedModelWrapper model=resource.getOptimizedModel();if(model==null || model.getModel()==null)return false;
        var snapshot=RailGeometrySnapshots.get(model.getModel());if(snapshot==null)return false;
        var sector=new Sector((int)Math.floor(x/16),(int)Math.floor(y/16),(int)Math.floor(z/16));
        org.joml.Matrix4f local=new org.joml.Matrix4f(matrix);local.m30((float)(x-sector.x*16D));local.m31((float)(y-sector.y*16D));local.m32((float)(z-sector.z*16D));
        Selection selection=new Selection(model,location,new MatrixKey(local),light,0);
        groups.computeIfAbsent(sector,k->new LinkedHashMap<>()).put(location,new Instance(selection,snapshot,resource,fallback));return true;
    }
    public void flush(){
        cache.beginFrame();
        try {
            // Layout metadata is bounded too. Reset only before current-pass resources are queued.
            if(layouts.values().stream().mapToInt(RegionPageLayout::locations).sum()>8192){cache.close();layouts.clear();}
            Map<PageKey,List<Selection>> wanted=new LinkedHashMap<>();
            Map<Instance,List<Selection>> fragments=new LinkedHashMap<>();
            Map<Selection,PageKey> placement=new HashMap<>();
            groups.forEach((sector,instances)->{
                var layout=layouts.computeIfAbsent(sector,k->new RegionPageLayout<>(RailGeometrySnapshots.PAGE_VERTICES));
                for(Instance instance:instances.values()) {
                    List<Selection> parts=new ArrayList<>();Selection original=instance.selection;
                    for(int n=0;n<instance.snapshot.chunks().size();n++) {
                        Fragment location=new Fragment(original.location,original.model,n);
                        Selection part=new Selection(original.model,location,original.matrix,original.light,n);
                        PageKey key=new PageKey(sector,layout.page(location,instance.snapshot.chunks().get(n).vertices()));
                        parts.add(part);placement.put(part,key);wanted.computeIfAbsent(key,k->new ArrayList<>()).add(part);
                    }
                    fragments.put(instance,parts);
                }
            });
            Map<PageKey,Page> ready=new HashMap<>();
            wanted.forEach((key,selected)->{
                try { Page page=cache.get(key,selected,Selection::location,s->{var snapshot=RailGeometrySnapshots.get(s.model.getModel());return snapshot==null?16_001:snapshot.chunks().get(s.chunk).vertices();},this::build);if(page!=null)ready.put(key,page); }
                catch(RuntimeException error){Init.LOGGER.error("MTR region page retained original rendering",error);}
            });
            Map<PageKey,List<Selection>> drawing=new LinkedHashMap<>();
            fragments.forEach((instance,parts)->{
                // Transaction per object: never duplicate half of a large model with its fallback.
                if(parts.stream().anyMatch(s->!ready.containsKey(placement.get(s)))) { instance.resource.render(instance.fallback,instance.selection.light);return; }
                parts.forEach(s->drawing.computeIfAbsent(placement.get(s),k->new ArrayList<>()).add(s));
                OptimizedModelWrapper remainder=instance.snapshot.remainder();
                if(remainder!=null)MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{instance.fallback.transform(holder,offset);try{CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(remainder,holder,instance.selection.light,family);}finally{holder.pop();}});
            });
            drawing.forEach((key,selected)->{
                Page page=ready.get(key);Map<MaterialProperties,List<RegionVertexRanges.Span>> ranges=new HashMap<>();
                for(Selection selection:selected)for(MaterialSpan span:page.spans.get(selection))ranges.computeIfAbsent(span.material,k->new ArrayList<>()).add(span.span);
                page.arrays.forEach((material,array)->RegionVertexRanges.select(array,ranges.getOrDefault(material,List.of())));
                Sector sector=key.sector;
                MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{holder.push();try{holder.translate(sector.x*16D-offset.getXMapped(),sector.y*16D-offset.getYMapped(),sector.z*16D-offset.getZMapped());CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(page.model,holder,0,family);}finally{holder.pop();}});
            });
        } finally { groups.clear(); }
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
            for(var entry:combined.entrySet()){Mesh mesh=entry.getValue().upload(mapping);page.meshes.add(mesh);page.arrays.put(entry.getKey(),new VertexArray(mesh,mapping));}
            page.model=OptimizedModelWrapper.fromUploadedModel(new OptimizedModel(new ArrayList<>(page.arrays.values())));return page;
        }catch(RuntimeException|Error error){page.close();throw error;}finally{GlStateTracker.restore();}
    }
    public void clear(){cache.close();groups.clear();layouts.clear();world=null;}
}
