"""Real LOD worker lifecycle; GL stubs reject any call from a worker/cleaner thread."""
from pathlib import Path
import shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
STUBS={
'com/mojang/blaze3d/systems/RenderSystem.java':'''package com.mojang.blaze3d.systems;public class RenderSystem{public static final Thread MAIN=Thread.currentThread();public static final java.util.Queue<Runnable> queue=new java.util.concurrent.ConcurrentLinkedQueue<>();public static boolean isOnRenderThread(){return Thread.currentThread()==MAIN;}public static void recordRenderCall(Runnable r){queue.add(r);}public static org.joml.Matrix4f getProjectionMatrix(){return new org.joml.Matrix4f();}public static void drain(){Runnable r;while((r=queue.poll())!=null)r.run();}}''',
'org/joml/Matrix4f.java':'package org.joml;public class Matrix4f{public float m11(){return 1;}public float m33(){return 0;}}',
'org/lwjgl/opengl/GL33.java':'''package org.lwjgl.opengl;public class GL33{public static final int GL_VIEWPORT=1,GL_UNSIGNED_INT=2;public static int allocations,deletions;public static void check(){if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())throw new AssertionError("GL call off render thread");}public static void glGetIntegerv(int type,java.nio.IntBuffer result){check();result.put(3,1080);}}''',
'org/mtr/mapping/mapper/OptimizedRenderer.java':'package org.mtr.mapping.mapper;public class OptimizedRenderer{public static boolean renderingShadows(){return false;}}',
'org/mtr/mapping/render/tool/Utilities.java':'package org.mtr.mapping.render.tool;public class Utilities{public static boolean canUseCustomShader(){return true;}}',
'org/mtr/mapping/render/tool/GlStateTracker.java':'package org.mtr.mapping.render.tool;public class GlStateTracker{public static void capture(){org.lwjgl.opengl.GL33.check();}public static void restore(){org.lwjgl.opengl.GL33.check();}}',
'org/mtr/mapping/render/vertex/VertexAttributeMapping.java':'package org.mtr.mapping.render.vertex;public class VertexAttributeMapping{}',
'org/mtr/mapping/render/object/VertexBuffer.java':'package org.mtr.mapping.render.object;public class VertexBuffer{public static final int USAGE_STATIC_DRAW=1;}',
'org/mtr/mapping/render/object/IndexBuffer.java':'package org.mtr.mapping.render.object;public class IndexBuffer{public boolean closed;public IndexBuffer(int a,int b){org.lwjgl.opengl.GL33.check();org.lwjgl.opengl.GL33.allocations++;}public void upload(java.nio.ByteBuffer b,int usage){org.lwjgl.opengl.GL33.check();}public void close(){org.lwjgl.opengl.GL33.check();if(closed)throw new AssertionError("double close");closed=true;org.lwjgl.opengl.GL33.deletions++;}}',
'org/mtr/mapping/render/object/VertexArray.java':'package org.mtr.mapping.render.object;public class VertexArray{public boolean closed;public VertexArray(){}public VertexArray(org.mtr.mapping.render.model.Mesh m,org.mtr.mapping.render.vertex.VertexAttributeMapping mapping){org.lwjgl.opengl.GL33.check();}public void close(){org.lwjgl.opengl.GL33.check();if(closed)throw new AssertionError("double close VAO");closed=true;}}',
'org/mtr/mapping/render/model/Material.java':'package org.mtr.mapping.render.model;public class Material{public boolean translucent,cutoutHack,writeDepthBuf=true;}',
'org/mtr/mapping/render/model/Mesh.java':'package org.mtr.mapping.render.model;public class Mesh{public org.mtr.mapping.render.object.VertexBuffer vertexBuffer;public Material materialProperties;public Mesh(org.mtr.mapping.render.object.VertexBuffer v,org.mtr.mapping.render.object.IndexBuffer i,Material m){vertexBuffer=v;materialProperties=m;}}',
'org/mtr/mapping/render/model/RawMesh.java':'''package org.mtr.mapping.render.model;public class RawMesh{public Material materialProperties=new Material();public java.util.List<Vertex> vertices=new java.util.ArrayList<>();public java.util.List<Object> faces=new java.util.ArrayList<>();public static class Vertex{public Position position=new Position();}public static class Position{public float getX(){return 1;}public float getY(){return 1;}public float getZ(){return 1;}}}''',
'org/mtr/mod/render/RailBatchGeometry.java':'package org.mtr.mod.render;public class RailBatchGeometry{public static org.mtr.mapping.render.model.RawMesh copy(org.mtr.mapping.render.model.RawMesh raw){var copy=new org.mtr.mapping.render.model.RawMesh();copy.vertices.addAll(raw.vertices);copy.faces.addAll(raw.faces);return copy;}}',
'org/mtr/mod/render/BoundedMeshSimplifier.java':'''package org.mtr.mod.render;public class BoundedMeshSimplifier{public static volatile java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),gate=new java.util.concurrent.CountDownLatch(1);public static int[] simplify(org.mtr.mapping.render.model.RawMesh mesh,double error){if(com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())throw new AssertionError("geometry computed on render thread");entered.countDown();try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();return null;}return new int[]{0,1,2};}}''',
'org/mtr/mod/render/ModelDetailGeometry.java':'package org.mtr.mod.render;public class ModelDetailGeometry{public static int[] indices(org.mtr.mapping.render.model.RawMesh raw,double size){return null;}}',
'org/mtr/mod/render/ModelPartVisibility.java':'package org.mtr.mod.render;public class ModelPartVisibility{public static int copies;public static void retain(org.mtr.mapping.render.object.VertexArray a,org.mtr.mapping.render.model.RawMesh r){}public static void copy(org.mtr.mapping.render.object.VertexArray a,org.mtr.mapping.render.object.VertexArray b){copies++;}public static void remove(org.mtr.mapping.render.object.VertexArray a){}}',
'org/mtr/mod/render/GlVertexAttributeCache.java':'package org.mtr.mod.render;public class GlVertexAttributeCache{public static void invalidate(){}}',
'org/mtr/mod/render/RenderPipelineMetrics.java':'package org.mtr.mod.render;public class RenderPipelineMetrics{public enum Family{TRAIN,OTHER}}',
'org/mtr/mod/Init.java':'package org.mtr.mod;public class Init{public static Log LOGGER=new Log();public static class Log{public void error(String message,Object... args){throw new AssertionError(message);}}}',
'DetailAsyncTest.java':r'''import org.mtr.mod.render.*;import org.mtr.mapping.render.model.*;import org.mtr.mapping.render.object.*;import org.mtr.mapping.render.vertex.*;import org.lwjgl.opengl.GL33;import com.mojang.blaze3d.systems.RenderSystem;import java.util.concurrent.*;
public class DetailAsyncTest{
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static VertexArray attach(int vertices){RawMesh raw=new RawMesh();for(int i=0;i<vertices;i++)raw.vertices.add(new RawMesh.Vertex());for(int i=0;i<16;i++)raw.faces.add(new Object());Mesh mesh=new Mesh(new VertexBuffer(),null,raw.materialProperties);VertexArray original=new VertexArray();ModelDetailCache.beginVehicleUpload();try{ModelDetailCache.uploaded(mesh,raw);ModelDetailCache.attach(original,mesh,new VertexAttributeMapping());}finally{ModelDetailCache.endVehicleUpload();}return original;}
 public static void main(String[] args)throws Exception{
  // Initialize the render-thread identity before submitting any worker.
  check(RenderSystem.isOnRenderThread(),"main thread");VertexArray original=attach(100);check(BoundedMeshSimplifier.entered.await(2,TimeUnit.SECONDS),"worker started");
  check(ModelDetailCache.select(original,RenderPipelineMetrics.Family.TRAIN,1e12)==original,"pending keeps complete original");check(GL33.allocations==0,"worker must not upload GL");
  BoundedMeshSimplifier.gate.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);VertexArray reduced=original;
  while(reduced==original && System.nanoTime()<deadline){ModelDetailCache.beginPass();reduced=ModelDetailCache.select(original,RenderPipelineMetrics.Family.TRAIN,1e12);Thread.yield();}
  check(reduced!=original && GL33.allocations==2 && ModelPartVisibility.copies==2,"publish both levels and preserve bounds");
  VertexArray finalReduced=reduced;Thread cleaner=new Thread(()->ModelDetailCache.close(original));cleaner.start();cleaner.join();check(GL33.deletions==0 && !finalReduced.closed,"cleaner queues GL deletion");RenderSystem.drain();check(GL33.deletions==2 && finalReduced.closed,"render-thread disposal exactly once");ModelDetailCache.close(original);check(GL33.deletions==2 && !original.closed,"source VAO retained");
  BoundedMeshSimplifier.entered=new CountDownLatch(1);BoundedMeshSimplifier.gate=new CountDownLatch(1);VertexArray pending=attach(100);check(BoundedMeshSimplifier.entered.await(2,TimeUnit.SECONDS),"second worker starts");ModelDetailCache.clear();BoundedMeshSimplifier.gate.countDown();ModelDetailCache.beginPass();check(GL33.allocations==2 && !pending.closed,"reload cancels pending CPU work without touching original");
  VertexArray excessive=attach(100001);check(ModelDetailCache.select(excessive,RenderPipelineMetrics.Family.TRAIN,1e12)==excessive,"excessive snapshot keeps original");ModelDetailCache.clear();
  System.out.println("PASS: geometry runs off render thread, original while pending, one publication, preserved bounds, cleaner GL dispatch, exact disposal, reload cancellation and CPU snapshot limit");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='mtr-detail-async-') as tmp:
 base=Path(tmp)
 for name,content in STUBS.items():p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 for name in ['ModelDetailCache','ScreenErrorBudget']:shutil.copy(ROOT/'fabric/src/main/java/org/mtr/mod/render'/f'{name}.java',base/'org/mtr/mod/render')
 subprocess.run(['javac','-d',tmp,*map(str,base.rglob('*.java'))],check=True)
 subprocess.run(['java','-cp',tmp,'DetailAsyncTest'],check=True)
