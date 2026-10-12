package dev.igntv.mtrengine.renderer;

import org.lwjgl.opengl.GL11;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.Mesh;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.object.IndexBuffer;
import org.mtr.mapping.render.object.VertexBuffer;
import org.mtr.mapping.render.tool.OffHeapAllocator;
import org.mtr.mapping.render.vertex.Vertex;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.vertex.VertexAttributeType;
import java.nio.ByteBuffer;

/**
 * Linear region mesh upload. Every region page is immutable and fully opaque;
 * indices are already triangulated and validated by RailGeometrySnapshots.
 * Skipping RawMesh.distinct() avoids a HashMap<Vertex> + HashSet<Face> rebuild
 * for each region upload (which was one of the largest Spark CPU hotspots).
 *
 * Unlike vanilla RawMesh.upload, this keeps all indexed vertices. It trades a
 * little extra GPU memory for dramatically less CPU preparation. Non-triangle
 * or unusual vertex layouts fall back to the stock uploader. Only static
 * region pages use this code; addon models retain the original implementation.
 */
public final class FastRegionMeshUploader {
    private static final boolean ENABLED=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.fastRegionUpload","true"));
    private FastRegionMeshUploader() {}

    public static Mesh upload(RawMesh source, VertexAttributeMapping mapping) {
        if (!ENABLED || source.vertices.isEmpty() || source.faces.isEmpty()
                || source.vertices.size() > 16_000 || mapping.strideVertex<=0
                || mapping.strideVertex>256) return source.upload(mapping);
        for (Face face:source.faces)if(face.vertices.length!=3)return source.upload(mapping);
        final int vertices=source.vertices.size(), faces=source.faces.size();
        final VertexAttributeSource buffer=VertexAttributeSource.VERTEX_BUFFER;
        final boolean position=mapping.sources.get(VertexAttributeType.POSITION)==buffer;
        final boolean color=mapping.sources.get(VertexAttributeType.COLOR)==buffer;
        final boolean uv=mapping.sources.get(VertexAttributeType.UV_TEXTURE)==buffer;
        final boolean light=mapping.sources.get(VertexAttributeType.UV_LIGHTMAP)==buffer;
        final boolean normal=mapping.sources.get(VertexAttributeType.NORMAL)==buffer;
        // The mapping library writes these attributes in precisely this order.
        final int stride=(position?12:0)+(color?4:0)+(uv?8:0)
                +(light?4:0)+(normal?3:0)+mapping.paddingVertex;
        if(stride!=mapping.strideVertex)return source.upload(mapping);
        Mesh mesh=new Mesh(new VertexBuffer(),new IndexBuffer(faces,GL11.GL_UNSIGNED_INT),source.materialProperties);
        ByteBuffer vertexBytes=null,indexBytes=null;
        try {
            vertexBytes=OffHeapAllocator.allocate(vertices * stride);
            for(Vertex v:source.vertices){
                if(position)vertexBytes.putFloat(v.position.getX()).putFloat(v.position.getY()).putFloat(v.position.getZ());
                if(color)vertexBytes.putInt(v.color);
                if(uv)vertexBytes.putFloat(v.u).putFloat(v.v);
                if(light)vertexBytes.putInt(v.light);
                if(normal){
                    float x=v.normal.getX(),y=v.normal.getY(),z=v.normal.getZ();
                    float len=(float)Math.sqrt(x*x+y*y+z*z);
                    if(len>0 && Float.isFinite(len)){x/=len;y/=len;z/=len;}
                    vertexBytes.put((byte)(x*127)).put((byte)(y*127)).put((byte)(z*127));
                }
                for(int p=0;p<mapping.paddingVertex;p++)vertexBytes.put((byte)0);
            }
            vertexBytes.flip();
            indexBytes=OffHeapAllocator.allocate(faces * 3 * Integer.BYTES);
            for(Face face:source.faces)for(int index:face.vertices){
                if(index<0 || index>=vertices)throw new IndexOutOfBoundsException("Region face index "+index+"/"+vertices);
                indexBytes.putInt(index);
            }
            indexBytes.flip();
            mesh.vertexBuffer.upload(vertexBytes,VertexBuffer.USAGE_STATIC_DRAW);
            mesh.indexBuffer.upload(indexBytes,VertexBuffer.USAGE_STATIC_DRAW);
            mesh.indexBuffer.setFaceCount(faces);
            return mesh;
        }catch(RuntimeException|Error exception){
            mesh.close();
            throw exception;
        }finally{
            if(vertexBytes!=null)OffHeapAllocator.free(vertexBytes);
            if(indexBytes!=null)OffHeapAllocator.free(indexBytes);
        }
    }
}
