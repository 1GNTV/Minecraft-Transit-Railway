package org.mtr.mod.render;

import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.*;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.mixin.OptimizedModelLayoutAccessor;
import org.mtr.mod.resource.OptimizedModelWrapper;

import java.util.*;

/**
 * Opt-in GPU-resident classic rail pages. Each 256-segment page is uploaded
 * only when its exact visible geometry, light, UVs or material change.
 * Unlike frame callbacks, uploaded buffers survive many stable frames.
 *
 * HELIX: disable the not-yet-verified GPU light path by default for shader parity:
 * -Dmtr.engine.classicGpu=true
 * Failures fall back to ClassicRailQuadBatch's proven vertex-consumer path.
 *
 * Render-thread only. This cache must be cleared on world/resource reload and
 * its pages must never be freed while callbacks from the same render tick are
 * pending. CPU-side rail geometry/light sampling is still performed each pass.
 */
public final class ClassicRailGpuCache {
    public static final boolean ENABLED=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.classicGpu","false"));
    private static final int MAX_PAGES=Math.max(16,Math.min(512,Integer.getInteger("mtr.engine.classicGpuPages",192)));
    private static final int MAX_VERTICES=Math.max(131_072,Math.min(2_097_152,Integer.getInteger("mtr.engine.classicGpuVertices",786_432)));
    private static final LinkedHashMap<Key,Page> PAGES=new LinkedHashMap<>(64,.75f,true);
    private static int residentVertices;
    private static long epoch,uploads,hits,evictions;
    private static boolean failed;

    private static final class Key {
        final Object rail;
        final Identifier texture;
        final int page;
        final int hash;
        Key(Object rail,Identifier texture,int page) {
            this.rail=rail;this.texture=texture;this.page=page;
            hash=31*(31*System.identityHashCode(rail)+texture.hashCode())+page;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object obj) {
            return obj instanceof Key other && rail==other.rail
                && texture.equals(other.texture) && page==other.page;
        }
    }

    private static final class Page implements AutoCloseable {
        final VertexArray array;
        final Mesh mesh;
        final OptimizedModelWrapper model;
        final double[] coordinates;
        final int[] lights;
        final float[] stripes;
        final int size;
        final float u1,v1,u2,v2;
        final double yOffset,smallOffset,originX,originY,originZ;
        final int color;
        final Set<Long> columns;
        long seen;
        Page(VertexArray array,Mesh mesh,OptimizedModelWrapper model,
             double[] coordinates,int[] lights,float[] stripes,
             float u1,float v1,float u2,float v2,double yOffset,double smallOffset,
             double originX,double originY,double originZ,int color,Set<Long> columns) {
            this.array=array;this.mesh=mesh;this.model=model;
            this.coordinates=coordinates;this.lights=lights;this.stripes=stripes;
            this.size=lights.length*8;this.u1=u1;this.v1=v1;this.u2=u2;this.v2=v2;
            this.yOffset=yOffset;this.smallOffset=smallOffset;this.originX=originX;
            this.originY=originY;this.originZ=originZ;this.color=color;this.columns=columns;
        }
        boolean matches(double[] coords,int[] light,float[] stripe,float u1,float v1,float u2,float v2,
                        double yOffset,double smallOffset,int color) {
            return this.color==color && Float.compare(this.u1,u1)==0
                && Float.compare(this.v1,v1)==0 && Float.compare(this.u2,u2)==0
                && Float.compare(this.v2,v2)==0 && Double.compare(this.yOffset,yOffset)==0
                && Double.compare(this.smallOffset,smallOffset)==0
                && Arrays.equals(coordinates,coords) && Arrays.equals(lights,light)
                && Arrays.equals(stripes,stripe);
        }
        @Override public void close(){array.close();mesh.close();}
    }
    private ClassicRailGpuCache(){}

    public static void beginFrame(){epoch++;}

    /** @return true only when a complete GPU page was safely queued. */
    public static boolean queue(Object rail,Identifier texture,int pageIndex,
                                double[] coordinates,int[] lights,float[] stripes,
                                float u1,float v1,float u2,float v2,
                                double yOffset,double smallOffset,int color) {
        if(!ENABLED||failed||rail==null||coordinates.length!=lights.length*10
                ||lights.length==0||lights.length>256||stripes.length!=lights.length
                ||!OptimizedRenderer.hasOptimizedRendering()||GlStateTracker.isGl4ES())return false;
        final Key key=new Key(rail,texture,pageIndex);
        Page page=PAGES.get(key);
        if(page!=null && page.matches(coordinates,lights,stripes,u1,v1,u2,v2,yOffset,smallOffset,color)){
            hits++;page.seen=epoch;
            schedule(page);
            return true;
        }
        final int requiredVertices=lights.length*8;
        // A cache rewrite may be needed for a moving viewpoint. Never delete
        // a page already referenced by main/shadow callbacks in this frame.
        if(page!=null && page.seen==epoch)return false;
        if(!room(page,requiredVertices))return false;
        try{
            Page replacement=upload(coordinates,lights,stripes,texture,u1,v1,u2,v2,
                                   yOffset,smallOffset,color);
            if(page!=null){PAGES.remove(key);release(page);}
            replacement.seen=epoch;
            PAGES.put(key,replacement);
            residentVertices+=replacement.size;
            uploads++;
            schedule(replacement);
            return true;
        }catch(RuntimeException error){
            failed=true;
            Init.LOGGER.error("GLACIER classic rail GPU path disabled after failure; using original rail vertex rendering",error);
            return false;
        }
    }

    private static boolean room(Page replacing,int newVertices) {
        var it=PAGES.values().iterator();
        while((PAGES.size()+(replacing==null?1:0)>MAX_PAGES
               ||residentVertices-(replacing==null?0:replacing.size)+newVertices>MAX_VERTICES)
               && it.hasNext()){
            Page candidate=it.next();
            if(candidate==replacing||candidate.seen==epoch)continue;
            release(candidate);it.remove();evictions++;
        }
        return PAGES.size()+(replacing==null?1:0)<=MAX_PAGES
               && residentVertices-(replacing==null?0:replacing.size)+newVertices<=MAX_VERTICES;
    }

    private static void schedule(Page page) {
        MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,(holder,offset)->{
            holder.push();
            try{
                holder.translate(page.originX-offset.getXMapped(),
                                 page.originY-offset.getYMapped(),
                                 page.originZ-offset.getZMapped());
                CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(
                    page.model,holder,0,RenderPipelineMetrics.Family.RAIL);
            }finally{holder.pop();}
        });
    }

    private static Page upload(double[] positions,int[] lights,float[] stripes,Identifier texture,
                               float u1,float v1,float u2,float v2,
                               double yOffset,double smallOffset,int color) {
        double ox=Math.floor(positions[0]/16D)*16D;
        double oy=Math.floor((positions[8]+yOffset)/16D)*16D;
        double oz=Math.floor(positions[1]/16D)*16D;
        MaterialProperties material=new MaterialProperties(OptimizedModel.ShaderType.CUTOUT,texture,null);
        RawMesh raw=new RawMesh(material);
        HashSet<Long> columns=new HashSet<>();
        float uu1=u1<0?0:u1,uu2=u2<0?1:u2;
        for(int i=0;i<lights.length;i++){
            int k=i*10;
            double x1=positions[k],z1=positions[k+1],x2=positions[k+2],z2=positions[k+3];
            double x3=positions[k+4],z3=positions[k+5],x4=positions[k+6],z4=positions[k+7];
            double y1=positions[k+8]+yOffset,y2=positions[k+9]+yOffset;
            float vv1=v1<0?0.1875F+stripes[i]:v1;
            float vv2=v2<0?0.3125F+stripes[i]:v2;
            int convertedLight=Utilities.exchangeLightmapUVBits(lights[i]);
            addQuad(raw,ox,oy,oz,uu1,vv1,uu2,vv2,color,convertedLight,
                x1,y1,z1,x2,y1+smallOffset,z2,x3,y2,z3,x4,y2+smallOffset,z4);
            addQuad(raw,ox,oy,oz,uu1,vv1,uu2,vv2,color,convertedLight,
                x2,y1+smallOffset,z2,x1,y1,z1,x4,y2+smallOffset,z4,x3,y2,z3);
            // The page must also be invalidated when a track in a neighbouring
            // chunk crosses this page, not just the first segment's chunk.
            for(int j=0;j<8;j+=2){
                double x=positions[k+j],z=positions[k+j+1];
                int sx=(int)Math.floor(x/16D),sz=(int)Math.floor(z/16D);
                columns.add((((long)sx)<<32)^(sz&0xffffffffL));
            }
        }
        var mappingBuilder=new VertexAttributeMapping.Builder();
        OptimizedModelLayoutAccessor.mtr$getDefaultMapping().sources.forEach(mappingBuilder::set);
        VertexAttributeMapping mapping=mappingBuilder
            .set(VertexAttributeType.UV_LIGHTMAP,VertexAttributeSource.VERTEX_BUFFER).build();
        Mesh mesh=null;
        VertexArray array=null;
        GlStateTracker.capture();
        try{
            mesh=FastRegionMeshUploader.upload(raw,mapping);
            array=new VertexArray(mesh,mapping);
            OptimizedModelWrapper model=OptimizedModelWrapper.fromUploadedModel(
                new OptimizedModel(List.of(array)));
            return new Page(array,mesh,model,coordinatesCopy(positions),lights.clone(),stripes.clone(),
                u1,v1,u2,v2,yOffset,smallOffset,ox,oy,oz,color,Set.copyOf(columns));
        }catch(RuntimeException|Error error){
            if(array!=null)array.close();
            if(mesh!=null)mesh.close();
            throw error;
        }finally{GlStateTracker.restore();}
    }

    private static double[] coordinatesCopy(double[] source){return source.clone();}

    private static void addQuad(RawMesh raw,double ox,double oy,double oz,
                                float u1,float v1,float u2,float v2,int color,int light,
                                double x1,double y1,double z1,double x2,double y2,double z2,
                                double x3,double y3,double z3,double x4,double y4,double z4) {
        int base=raw.vertices.size();
        vertex(raw,x1-ox,y1-oy,z1-oz,u1,v2,color,light);
        vertex(raw,x2-ox,y2-oy,z2-oz,u2,v2,color,light);
        vertex(raw,x3-ox,y3-oy,z3-oz,u2,v1,color,light);
        vertex(raw,x4-ox,y4-oy,z4-oz,u1,v1,color,light);
        raw.faces.add(new Face(new int[]{base,base+1,base+2}));
        raw.faces.add(new Face(new int[]{base,base+2,base+3}));
    }

    private static void vertex(RawMesh raw,double x,double y,double z,
                               float u,float v,int color,int light){
        Vertex vertex=new Vertex();
        vertex.position=new Vector3f((float)x,(float)y,(float)z);
        vertex.normal=new Vector3f(0,1,0);
        vertex.u=u;vertex.v=v;vertex.color=color;vertex.light=light;
        raw.vertices.add(vertex);
    }

    private static void release(Page page){
        page.close();
        residentVertices-=page.size;
    }
    public static void invalidateColumns(Set<Long> changed) {
        if(changed.isEmpty())return;
        var it=PAGES.values().iterator();
        while(it.hasNext()){
            Page page=it.next();
            boolean dirty=false;
            for(long column:changed)if(page.columns.contains(column)){dirty=true;break;}
            if(dirty){release(page);it.remove();}
        }
    }
    public static void clear(){
        PAGES.values().forEach(ClassicRailGpuCache::release);
        PAGES.clear();residentVertices=0;failed=false;
    }
}
