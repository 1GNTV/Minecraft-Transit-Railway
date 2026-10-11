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
    private record Selection(OptimizedModelWrapper model,String location,List<Float> matrix,int light){}
    private record Instance(Selection selection,RailGeometrySnapshots.Snapshot snapshot,StoredModelResourceBase resource,StoredMatrixTransformations fallback){}
    private record MaterialSpan(MaterialProperties material,RegionVertexRanges.Span span){}
    private static final class Page implements AutoCloseable{
        final Map<MaterialProperties,VertexArray> arrays=new LinkedHashMap<>();final List<Mesh> meshes=new ArrayList<>();
        final Map<Selection,List<MaterialSpan>> spans=new HashMap<>();OptimizedModelWrapper model;
        @Override public void close(){arrays.values().forEach(a->{RegionVertexRanges.remove(a);a.close();});meshes.forEach(Mesh::close);}
    }
    private final PersistentRegionCache<Sector,Selection,String,Page> cache=new PersistentRegionCache<>(64,256_000,16_000);
    private final Map<Sector,LinkedHashMap<String,Instance>> groups=new LinkedHashMap<>();
    private final RenderPipelineMetrics.Family family;
    private Object world;
    public StaticRegionRenderer(RenderPipelineMetrics.Family family){this.family=family;}
    public boolean queue(StoredModelResourceBase resource,StoredMatrixTransformations fallback,String location,double x,double y,double z,org.joml.Matrix4f matrix,int light){
        if(!RailGeometrySnapshots.ENABLED || !OptimizedRenderer.hasOptimizedRendering() || GlStateTracker.isGl4ES())return false;
        Object current=MinecraftClient.getInstance().getWorldMapped().data;if(current!=world){clear();world=current;}
        OptimizedModelWrapper model=resource.getOptimizedModel();if(model==null || model.getModel()==null)return false;
        var snapshot=RailGeometrySnapshots.get(model.getModel());if(snapshot==null)return false;
        var sector=new Sector((int)Math.floor(x/16),(int)Math.floor(y/16),(int)Math.floor(z/16));
        org.joml.Matrix4f local=new org.joml.Matrix4f(matrix);local.m30((float)(x-sector.x*16D));local.m31((float)(y-sector.y*16D));local.m32((float)(z-sector.z*16D));
        float[] values=new float[16];local.get(values);List<Float> signature=new ArrayList<>(16);for(float value:values)signature.add(value);
        Selection selection=new Selection(model,location,List.copyOf(signature),light);
        groups.computeIfAbsent(sector,k->new LinkedHashMap<>()).put(location,new Instance(selection,snapshot,resource,fallback));return true;
    }
    public void flush(){
        cache.beginFrame();
        try{groups.forEach((sector,instances)->{
            List<Selection> selected=instances.values().stream().map(Instance::selection).toList();Page page=null;
            try{page=cache.get(sector,selected,Selection::location,s->{var p=RailGeometrySnapshots.get(s.model.getModel());return p==null?16_001:p.vertices();},content->build(content));}
            catch(RuntimeException error){Init.LOGGER.error("MTR static region reverted to individual rendering",error);}
            if(page==null){instances.values().forEach(i->i.resource.render(i.fallback,i.selection.light));return;}
            Map<MaterialProperties,List<RegionVertexRanges.Span>> ranges=new HashMap<>();
            for(Selection s:selected)for(MaterialSpan span:page.spans.get(s))ranges.computeIfAbsent(span.material,k->new ArrayList<>()).add(span.span);
            page.arrays.forEach((material,array)->RegionVertexRanges.select(array,ranges.getOrDefault(material,List.of())));
            OptimizedModelWrapper batch=page.model;
            MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{holder.push();try{holder.translate(sector.x*16D-offset.getXMapped(),sector.y*16D-offset.getYMapped(),sector.z*16D-offset.getZMapped());CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(batch,holder,0,family);}finally{holder.pop();}});
        });}finally{groups.clear();}
    }
    private Page build(List<Selection> content){
        Page page=new Page();Map<MaterialProperties,RawMesh> combined=new LinkedHashMap<>();
        for(Selection s:content){
            float[] values=new float[16];for(int i=0;i<16;i++)values[i]=s.matrix.get(i);Matrix4f transform=new Matrix4f(new org.joml.Matrix4f().set(values));
            var snapshot=RailGeometrySnapshots.get(s.model.getModel());if(snapshot==null)return null;
            List<MaterialSpan> spans=new ArrayList<>();
            for(RawMesh source:snapshot.meshes()){
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
    public void clear(){cache.close();groups.clear();world=null;}
}
