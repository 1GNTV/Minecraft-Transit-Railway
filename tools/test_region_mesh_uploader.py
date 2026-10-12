"""Compile production region GPU uploader and verify vertex/index buffers byte-for-byte."""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"fabric/src/main/java/org/mtr/mod/render/FastRegionMeshUploader.java"
STUBS={
"org/lwjgl/opengl/GL11.java":"""package org.lwjgl.opengl;public class GL11 {public static final int GL_UNSIGNED_INT=5125;}""",
"org/mtr/mapping/render/tool/OffHeapAllocator.java":"""package org.mtr.mapping.render.tool;import java.nio.*;public class OffHeapAllocator {
public static ByteBuffer allocate(int n){return ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder());}
public static void free(ByteBuffer b){}
}""",
"org/mtr/mapping/render/vertex/VertexAttributeSource.java":"""package org.mtr.mapping.render.vertex;public enum VertexAttributeSource{VERTEX_BUFFER,GLOBAL}""",
"org/mtr/mapping/render/vertex/VertexAttributeType.java":"""package org.mtr.mapping.render.vertex;public enum VertexAttributeType{POSITION,COLOR,UV_TEXTURE,UV_LIGHTMAP,NORMAL}""",
"org/mtr/mapping/render/vertex/VertexAttributeMapping.java":"""
package org.mtr.mapping.render.vertex;import java.util.*;
public class VertexAttributeMapping {
 public int strideVertex=32,paddingVertex=1;
 public final Map<VertexAttributeType,VertexAttributeSource> sources=new EnumMap<>(VertexAttributeType.class);
 public VertexAttributeMapping(){for(var t:VertexAttributeType.values())sources.put(t,VertexAttributeSource.VERTEX_BUFFER);}
}
""",
"org/mtr/mapping/render/vertex/Vertex.java":"""
package org.mtr.mapping.render.vertex;
public class Vertex{
 public static class Vector{public float x,y,z;public Vector(float x,float y,float z){this.x=x;this.y=y;this.z=z;}public float getX(){return x;}public float getY(){return y;}public float getZ(){return z;}}
 public Vector position=new Vector(0,0,0),normal=new Vector(0,2,0);
 public int color=0x44ff33cc,light=2341;
 public float u=.25f,v=.5f;
}
""",
"org/mtr/mapping/render/model/Face.java":"""
package org.mtr.mapping.render.model;public class Face{public final int[] vertices;public Face(int... ids){vertices=ids;}}
""",
"org/mtr/mapping/render/model/Mesh.java":"""
package org.mtr.mapping.render.model;import org.mtr.mapping.render.object.*;
public class Mesh implements AutoCloseable{
 public final VertexBuffer vertexBuffer;public final IndexBuffer indexBuffer;public final Object properties;
 public Mesh(VertexBuffer v,IndexBuffer i,Object p){vertexBuffer=v;indexBuffer=i;properties=p;}
 public void close(){}
}
""",
"org/mtr/mapping/render/model/RawMesh.java":"""
package org.mtr.mapping.render.model;import java.util.*;import org.mtr.mapping.render.vertex.*;
public class RawMesh{
 public final List<Vertex> vertices=new ArrayList<>();
 public final List<Face> faces=new ArrayList<>();
 public final Object materialProperties=new Object();
 public static int fallbackCalls;
 public Mesh upload(VertexAttributeMapping m){fallbackCalls++;return new Mesh(new org.mtr.mapping.render.object.VertexBuffer(),new org.mtr.mapping.render.object.IndexBuffer(0,0),materialProperties);}
}
""",
"org/mtr/mapping/render/object/VertexBuffer.java":"""
package org.mtr.mapping.render.object;import java.nio.*;
public class VertexBuffer{
 public static final int USAGE_STATIC_DRAW=1;
 public ByteBuffer data;
 public void upload(ByteBuffer bytes,int usage){
  if(usage!=USAGE_STATIC_DRAW)throw new AssertionError("vertex usage");
  data=ByteBuffer.allocate(bytes.remaining()).order(ByteOrder.nativeOrder());data.put(bytes.duplicate());data.flip();
 }
}
""",
"org/mtr/mapping/render/object/IndexBuffer.java":"""
package org.mtr.mapping.render.object;import java.nio.*;
public class IndexBuffer{
 public static final int USAGE_STATIC_DRAW=1;public final int type;public int count;public ByteBuffer data;
 public IndexBuffer(int n,int t){count=n;type=t;}
 public void upload(ByteBuffer b,int u){
  if(u!=USAGE_STATIC_DRAW)throw new AssertionError("index usage");
  data=ByteBuffer.allocate(b.remaining()).order(ByteOrder.nativeOrder());data.put(b.duplicate());data.flip();
 }
 public void setFaceCount(int n){count=n;}
}
""",
"FastRegionUploadTest.java":"""
import org.mtr.mod.render.FastRegionMeshUploader;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.vertex.*;
import java.nio.*;
public class FastRegionUploadTest {
 static void check(boolean valid,String msg){if(!valid)throw new AssertionError(msg);}
 public static void main(String... args){
  var original=new RawMesh();
  for(int i=0;i<4;i++){Vertex v=new Vertex();v.position=new Vertex.Vector(i,i+1,i+2);v.normal=new Vertex.Vector(0,2,0);original.vertices.add(v);}
  original.faces.add(new Face(0,1,2));original.faces.add(new Face(0,2,3));
  var mapping=new VertexAttributeMapping();
  Mesh gpu=FastRegionMeshUploader.upload(original,mapping);
  check(RawMesh.fallbackCalls==0,"unexpected original uploader");
  check(gpu.vertexBuffer.data.capacity()==4*32,"vertex stride");
  check(gpu.indexBuffer.data.capacity()==2*3*4,"index buffer");
  check(gpu.indexBuffer.count==2,"face count");
  var floats=gpu.vertexBuffer.data.order(ByteOrder.nativeOrder());
  check(floats.getFloat(0)==0f&&floats.getFloat(4)==1f&&floats.getFloat(8)==2f,"positions");
  check(floats.getInt(12)==0x44ff33cc,"color");
  check(floats.getInt(24)==2341,"lighting");
  check(floats.get(28)==0&&floats.get(29)==127&&floats.get(30)==0,"normal normalization");
  check(floats.get(31)==0,"padding");
  int[] expected={0,1,2,0,2,3};
  for(int i=0;i<expected.length;i++)check(gpu.indexBuffer.data.getInt(i*4)==expected[i],"face order "+i);
  check(original.vertices.size()==4 && original.faces.get(0).vertices[0]==0,"input must remain immutable");
  mapping.strideVertex=60;
  FastRegionMeshUploader.upload(original,mapping);
  check(RawMesh.fallbackCalls==1,"unknown layout must use original");
  mapping.strideVertex=32;
  original.faces.add(new Face(0,1,2,3));
  FastRegionMeshUploader.upload(original,mapping);
  check(RawMesh.fallbackCalls==2,"nontriangular mesh must use original");
  System.out.println("PASS: 32-byte vertices, six ordered indices, normals, light, fallback, no vertex dedup");
 }
}
"""
}
with tempfile.TemporaryDirectory(prefix="mtr-region-linear-") as name:
    root=Path(name)
    for relative, code in STUBS.items():
        p=root/relative
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(code)
    subprocess.run(["javac","-d",name,str(SOURCE),*map(str,root.rglob("*.java"))],check=True)
    subprocess.run(["java","-cp",name,"FastRegionUploadTest"],check=True)
