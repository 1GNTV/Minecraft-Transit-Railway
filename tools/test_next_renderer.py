"""Exercise real instancing/frame helpers with recording GL boundaries, without a game context."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
"org/mtr/mod/render/OculusInstanceRenderer.java": "package org.mtr.mod.render; import java.util.*; public class OculusInstanceRenderer {public static boolean draw(List<?> x,int a,int b){return false;}public static boolean shaderBridgeReady(){return false;}public static void clear(){}}",
"org/mtr/mod/render/RegionVertexRanges.java": "package org.mtr.mod.render; import org.mtr.mapping.render.object.VertexArray; public class RegionVertexRanges {public static boolean contains(VertexArray a){return false;}}",
"org/mtr/mod/render/ModelPartVisibility.java": "package org.mtr.mod.render; import org.mtr.mapping.render.object.VertexArray;import org.mtr.mapping.render.vertex.VertexAttributeState; public class ModelPartVisibility {public static boolean visible(VertexArray a,VertexAttributeState s){return true;}public static org.mtr.mod.render.BoundedDrawGrouping.Rect screenBounds(VertexArray a,VertexAttributeState s){return null;}}",
"com/mojang/blaze3d/systems/RenderSystem.java": "package com.mojang.blaze3d.systems; public class RenderSystem {public static boolean isOnRenderThread(){return true;} public static void recordRenderCall(Runnable r){r.run();}}",
"org/lwjgl/opengl/GL.java": "package org.lwjgl.opengl; public class GL { public static C getCapabilities(){return new C();} public static class C {public boolean OpenGL33=!Boolean.getBoolean(\"test.unsupported\");}}",
"org/lwjgl/opengl/GL33.java": """package org.lwjgl.opengl;
import java.nio.*; import java.util.*;
public class GL33 {
 public static final int GL_ALREADY_SIGNALED=1,GL_CONDITION_SATISFIED=2,GL_ARRAY_BUFFER=3,GL_STREAM_DRAW=4,GL_TRIANGLES=5,GL_SYNC_GPU_COMMANDS_COMPLETE=6,GL_ELEMENT_ARRAY_BUFFER_BINDING=7,GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING=8,GL_ELEMENT_ARRAY_BUFFER=9;
 public static int id=100, draws, uploads, deletes, polls, capacity, vao; public static boolean ready;
 public static int allocations; public static long fence=1000; public static float[] lastUpload; public static Set<Integer> live=new HashSet<>();
 public static int glClientWaitSync(long s,int flags,long timeout){if(flags!=0||timeout!=0)throw new AssertionError("blocking poll"); polls++;return ready?GL_ALREADY_SIGNALED:0;}
 public static void glDeleteSync(long s){} public static long glFenceSync(int x,int y){return ++fence;}
 public static int glGenBuffers(){live.add(++id);return id;} public static int glGenVertexArrays(){live.add(++id);return id;}
 public static void glDeleteBuffers(int x){if(!live.remove(x))throw new AssertionError("double delete");deletes++;}
 public static void glDeleteVertexArrays(int x){if(!live.remove(x))throw new AssertionError("source VAO deleted");deletes++;}
 public static void glBindBuffer(int a,int b){} public static void glBindVertexArray(int a){vao=a;}
 public static int glGetInteger(int a){return 42;} public static int glGetVertexAttribi(int a,int b){return 43;}
 public static void glBufferData(int target,long size,int usage){if(size>131072)throw new AssertionError("unbounded GPU buffer");capacity=(int)size; allocations++;}
 public static void glBufferSubData(int target,long offset,ByteBuffer b){uploads++;lastUpload=new float[b.remaining()/4];b.asFloatBuffer().get(lastUpload);}
 public static void glVertexAttribDivisor(int l,int d){} public static void glDrawElementsInstanced(int mode,int indices,int type,long offset,int count){if(count<4||count>1024)throw new AssertionError();draws++;}
}""",
"org/mtr/mapping/holder/Matrix4f.java": "package org.mtr.mapping.holder; public class Matrix4f {public float[] data=new float[16]; public Matrix4f(float value){for(int i=0;i<16;i++)data[i]=value+i/100F;}}",
"org/mtr/mapping/mapper/OptimizedRenderer.java": "package org.mtr.mapping.mapper; public class OptimizedRenderer {public static boolean shadows;public static boolean renderingShadows(){return shadows;}}",
"org/mtr/mapping/render/tool/Utilities.java": "package org.mtr.mapping.render.tool; import java.nio.*;import org.mtr.mapping.holder.Matrix4f;public class Utilities {public static boolean custom=true;public static boolean canUseCustomShader(){return custom;}public static void store(Matrix4f m,FloatBuffer b){for(int i=0;i<16;i++)b.put(i,m.data[i]);}}",
"org/mtr/mapping/render/tool/GlStateTracker.java": "package org.mtr.mapping.render.tool; public class GlStateTracker {public static boolean isGl4ES(){return false;}}",
"org/mtr/mapping/render/vertex/VertexAttributeSource.java": "package org.mtr.mapping.render.vertex; public enum VertexAttributeSource {GLOBAL,VERTEX_BUFFER,INSTANCE_BUFFER}",
"org/mtr/mapping/render/vertex/VertexAttributeType.java": "package org.mtr.mapping.render.vertex; public enum VertexAttributeType {POSITION(0,1),COLOR(1,1),UV_TEXTURE(2,1),UV_OVERLAY(3,1),UV_LIGHTMAP(4,1),NORMAL(5,1),MATRIX_MODEL(6,4);public final int location,span;VertexAttributeType(int l,int s){location=l;span=s;}public void toggleAttributeArray(boolean b){}public void setupAttributePointer(int s,int p){}}",
"org/mtr/mapping/render/vertex/VertexAttributeMapping.java": "package org.mtr.mapping.render.vertex; import java.util.*;public class VertexAttributeMapping {public Map<VertexAttributeType,VertexAttributeSource> sources=new EnumMap<>(VertexAttributeType.class);public Map<VertexAttributeType,Integer> pointers=new EnumMap<>(VertexAttributeType.class);public int strideVertex=32;public VertexAttributeMapping(){for(var t:VertexAttributeType.values()){sources.put(t,t==VertexAttributeType.POSITION||t==VertexAttributeType.NORMAL||t==VertexAttributeType.UV_TEXTURE?VertexAttributeSource.VERTEX_BUFFER:VertexAttributeSource.GLOBAL);pointers.put(t,0);}}}",
"org/mtr/mapping/render/vertex/VertexAttributeState.java": "package org.mtr.mapping.render.vertex; import org.mtr.mapping.holder.Matrix4f; public class VertexAttributeState {public Object position,normal;public Float textureU,textureV;public Integer color=1,lightmapUV=2,overlayUV=3;public Matrix4f matrix4f;public void apply(){}}",
"org/mtr/mapping/render/batch/MaterialProperties.java": "package org.mtr.mapping.render.batch; import org.mtr.mapping.render.vertex.VertexAttributeState;public class MaterialProperties {public boolean translucent,cutoutHack,writeDepthBuf=true;public VertexAttributeState vertexAttributeState=new VertexAttributeState();}",
"org/mtr/mapping/render/object/IndexBuffer.java": "package org.mtr.mapping.render.object;public class IndexBuffer {public int indexType=2;public int getVertexCount(){return 6;}}",
"org/mtr/mapping/render/object/VertexArray.java": "package org.mtr.mapping.render.object;import org.mtr.mapping.render.vertex.*;import org.mtr.mapping.render.batch.*;import org.mtr.mod.render.VertexArrayIdentity;public class VertexArray implements VertexArrayIdentity {public int id=1;public MaterialProperties materialProperties=new MaterialProperties();public VertexAttributeMapping mapping=new VertexAttributeMapping();public IndexBuffer indexBuffer=new IndexBuffer();public void bind(){}public int mtr$vaoId(){return id;}}",
"org/mtr/mod/render/GlVertexAttributeCache.java": "package org.mtr.mod.render; public class GlVertexAttributeCache {public static void invalidate(){}}",
"org/mtr/mod/render/RenderPipelineMetrics.java": "package org.mtr.mod.render; public class RenderPipelineMetrics {public enum Family{RAIL,TRAIN,DECORATION,OTHER}public static void drawing(Family f){}public static void instancedDraw(int i,int n){}}",
"org/mtr/mod/Init.java": "package org.mtr.mod;public class Init {public static Log LOGGER=new Log();public static class Log {public void info(String s,Object... a){} public void error(String s,Object... a){throw new AssertionError(s);}}}",
"NextTest.java": r'''import java.util.*;import org.mtr.mod.render.*;import org.mtr.mapping.render.object.*;import org.mtr.mapping.render.vertex.*;import org.mtr.mapping.render.tool.*;import org.mtr.mapping.holder.*;import org.lwjgl.opengl.*;
public class NextTest {
 static int ordinary;
 static class Call implements RenderCallData {VertexArray array;VertexAttributeState state=new VertexAttributeState();Call(VertexArray a,int n){array=a;state.matrix4f=new Matrix4f(n);}public VertexArray mtr$array(){return array;}public VertexAttributeState mtr$attributes(){return state;}public RenderPipelineMetrics.Family mtr$family(){return RenderPipelineMetrics.Family.RAIL;}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 static List<Call> calls(VertexArray a,int n,int base){var result=new ArrayList<Call>();for(int i=0;i<n;i++)result.add(new Call(a,base+i));return result;}
 static void draw(List<Call> c){InstancedModelRenderer.draw(c,x->ordinary++);}
 public static void main(String[] args){
  var memo=new FrameMemoizer<Object,String>(1);Object a=new Object(),b=new Object();int[] computes={0};
  long generation=ShaderReloadPolicy.generation();check(!ShaderReloadPolicy.canSkip(a,true));ShaderReloadPolicy.completed(a,generation,true);
  check(ShaderReloadPolicy.canSkip(a,true)==!Boolean.getBoolean("test.shaderCacheDisabled"));check(!ShaderReloadPolicy.canSkip(a,false));check(!ShaderReloadPolicy.canSkip(b,true));
  ShaderReloadPolicy.invalidate();check(!ShaderReloadPolicy.canSkip(a,true));ShaderReloadPolicy.completed(a,generation,true);check(!ShaderReloadPolicy.canSkip(a,true));
  ShaderReloadPolicy.completed(a,ShaderReloadPolicy.generation(),true);check(ShaderReloadPolicy.canSkip(a,true)==!Boolean.getBoolean("test.shaderCacheDisabled"));ShaderReloadPolicy.completed(a,ShaderReloadPolicy.generation(),false);check(!ShaderReloadPolicy.canSkip(a,true));
  int[] triangles=new int[600];for(int f=0;f<200;f++){int v=(f%20)*100+f/20;for(int n=0;n<3;n++)triangles[f*3+n]=v+n;}
  int[] ordered=TriangleOrder.optimize(triangles,2048);check(TriangleOrder.misses(ordered)<TriangleOrder.misses(triangles));
  check(memo.get(a,0,()->"off"+(++computes[0])).equals("off1"));memo.beginFrame();
  check(memo.get(a,0,()->"pose"+(++computes[0])).equals("pose2"));check(memo.get(a,0,()->"bad").equals("pose2"));
  check(memo.get(a,1,()->"changed").equals("changed"));memo.get(b,0,()->"overflow");check(memo.size()==1);
  memo.beginFrame();check(memo.get(a,1,()->"next").equals("next"));memo.endFrame();check(memo.size()==0);check(memo.get(a,1,()->"outside").equals("outside"));
  var vao=new VertexArray();var c=calls(vao,4,1);
  if(args.length>0){draw(c);check(ordinary==4&&GL33.draws==0&&GL33.live.isEmpty());System.out.println("PASS: "+args[0]);return;}
  draw(c);check(GL33.draws==1&&ordinary==0&&GL33.uploads==1&&vao.id==1);
  for(int i=0;i<4;i++)for(int j=0;j<16;j++)check(GL33.lastUpload[i*19+j]==c.get(i).state.matrix4f.data[j]);
  draw(c);check(GL33.draws==2&&GL33.uploads==1); // same data can be reused even with a pending fence
  draw(calls(vao,4,20));draw(calls(vao,4,30));check(GL33.uploads==3&&GL33.live.size()==6);
  draw(calls(vao,4,40));check(ordinary==4&&GL33.draws==4&&GL33.polls>0); // no wait, all three pages busy
  GL33.ready=true;draw(calls(vao,4,40));check(GL33.draws==5&&GL33.uploads==4);
  int allocations=GL33.allocations;for(int i=0;i<100;i++)draw(calls(vao,4,100+i));check(GL33.allocations==allocations);check(GL33.draws==105);
  Utilities.custom=false;draw(c);Utilities.custom=true;check(ordinary==8); // preserve Oculus path
  vao.materialProperties.translucent=true;draw(c);vao.materialProperties.translucent=false;check(ordinary==12);
  vao.materialProperties.vertexAttributeState.color=null;vao.materialProperties.vertexAttributeState.lightmapUV=null;vao.materialProperties.vertexAttributeState.overlayUV=null;
  c.get(2).state.lightmapUV=99;c.get(1).state.color=0x11223344;draw(c);check(ordinary==12); // per-instance attributes no longer split groups
  check(Float.floatToRawIntBits(GL33.lastUpload[2*19+17])==99*65536); // native-endian short pair
  check(Float.floatToRawIntBits(GL33.lastUpload[1*19+16])==0x44332211);
  vao.id=0;draw(c);check(ordinary==16);vao.id=1;
  draw(calls(new VertexArray(),1025,1));check(ordinary==17&&GL33.lastUpload.length==1024*19);
  for(int i=0;i<70;i++)draw(calls(new VertexArray(),4,i));check(GL33.live.size()<=64*3*2);
  InstancedModelRenderer.clear();check(GL33.live.isEmpty()&&vao.id==1);InstancedModelRenderer.clear();
  var ranges=new ArrayList<String>();ConsecutiveRuns.visit(List.of(1,1,2,1,1),2,Integer::equals,(s,e)->ranges.add(s+":"+e));check(ranges.equals(List.of("0:2","2:3","3:5")));
  System.out.println("PASS: matrix packing, stable uploads, bounded triple buffering, zero-time fence polls, shader/state/order fallback, source ownership, frame/revision lifetime");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix="mtr-next-") as tmp:
    base = Path(tmp)
    for relative, content in STUBS.items():
        path = base / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    for name in ["InstancedModelRenderer", "RenderCallData", "VertexArrayIdentity", "ConsecutiveRuns", "FrameMemoizer", "TriangleOrder", "ShaderReloadPolicy", "InstanceShaderSource", "InstanceValues", "BoundedDrawGrouping"]:
        shutil.copy(ROOT / "fabric/src/main/java/org/mtr/mod/render" / (name + ".java"), base / "org/mtr/mod/render")
    subprocess.run(["javac", "-d", tmp] + [str(p) for p in base.rglob("*.java")], check=True)
    for args in [["NextTest"], ["-Dmtr.next.instances=false", "NextTest", "disabled"], ["-Dtest.unsupported=true", "NextTest", "unsupported"], ["-Dmtr.next.shaderReloadCache=false", "-Dtest.shaderCacheDisabled=true", "NextTest"]]:
        subprocess.run(["java", "-cp", tmp] + args, check=True)
