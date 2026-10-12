"""Compile the shipping indirect-region renderer against recording GL stubs."""
from pathlib import Path
import tempfile
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SHIPPING=ROOT/"fabric/src/main/java/org/mtr/mod/render/RegionVertexRanges.java"
STUBS={
"org/lwjgl/BufferUtils.java":"""
package org.lwjgl;
import java.nio.*;
public class BufferUtils{
 public static IntBuffer createIntBuffer(int n){return IntBuffer.allocate(n);}
 public static PointerBuffer createPointerBuffer(int n){return new PointerBuffer(n);}
}
""",
"org/lwjgl/PointerBuffer.java":"""
package org.lwjgl;
public class PointerBuffer{
 int cap,position,limit;
 public PointerBuffer(int n){cap=n;limit=n;}
 public int capacity(){return cap;}
 public PointerBuffer clear(){position=0;limit=cap;return this;}
 public PointerBuffer put(long n){position++;return this;}
 public PointerBuffer flip(){limit=position;position=0;return this;}
}
""",
"org/lwjgl/opengl/GL.java":"""
package org.lwjgl.opengl;
public class GL{
 public static class Cap {public boolean OpenGL43=true;}
 public static Cap caps=new Cap();
 public static Cap getCapabilities(){return caps;}
}
""",
"org/lwjgl/opengl/GL33.java":"""
package org.lwjgl.opengl;
import org.lwjgl.PointerBuffer;
import java.nio.IntBuffer;
public class GL33{
 public static final int GL_UNSIGNED_BYTE=1, GL_UNSIGNED_SHORT=2;
 public static int legacy, single;
 public static void glDrawElements(int mode,int count,int type,long offset){single++;}
 public static void glMultiDrawElements(int mode,IntBuffer counts,int type,PointerBuffer offsets){legacy++;}
}
""",
"org/lwjgl/opengl/GL43.java":"""
package org.lwjgl.opengl;
import java.nio.ByteBuffer;
public class GL43{
 public static final int GL_DRAW_INDIRECT_BUFFER=12,GL_DRAW_INDIRECT_BUFFER_BINDING=13,GL_DYNAMIC_DRAW=14;
 public static int current,uploads,draws,deletions,next=1,lastFirst,lastSecond;
 public static int glGetInteger(int cap){return current;}
 public static int glGenBuffers(){return ++next;}
 public static void glDeleteBuffers(int id){deletions++;}
 public static void glBindBuffer(int target,int id){current=id;}
 public static void glBufferData(int target,ByteBuffer data,int usage){
  if(data.remaining()%20!=0)throw new AssertionError("bad command packing");
  uploads++;
  lastFirst=data.getInt(data.position()+8);
  lastSecond=data.getInt(data.position()+28);
  if(data.getInt(data.position()+4)!=1 || data.getInt(data.position()+24)!=1)
   throw new AssertionError("instances not one");
 }
 public static void glMultiDrawElementsIndirect(int mode,int type,long offset,int count,int stride){
  if(current==0 || offset!=0 || count<2 || stride!=0)throw new AssertionError("bad indirect dispatch");
  draws++;
 }
}
""",
"org/mtr/mapping/mapper/OptimizedRenderer.java":"""
package org.mtr.mapping.mapper;
public class OptimizedRenderer{public static boolean shadows;public static boolean renderingShadows(){return shadows;}}
""",
"org/mtr/mapping/render/tool/Utilities.java":"""
package org.mtr.mapping.render.tool;
public class Utilities{public static boolean shaderless=true;public static boolean canUseCustomShader(){return shaderless;}}
""",
"org/mtr/mapping/render/tool/GlStateTracker.java":"""
package org.mtr.mapping.render.tool;
public class GlStateTracker{public static boolean isGl4ES(){return false;}}
""",
"org/mtr/mapping/render/object/VertexArray.java":"""
package org.mtr.mapping.render.object;
public class VertexArray{
 public final IndexBuffer indexBuffer=new IndexBuffer();
 public static class IndexBuffer {public int indexType=2;public int getVertexCount(){return 32;}}
}
""",
"EngineIndirectTest.java":"""
import org.mtr.mod.render.RegionVertexRanges;
import org.mtr.mod.render.RegionVertexRanges.Span;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.lwjgl.opengl.*;
import java.util.*;
public class EngineIndirectTest {
 static void check(boolean c,String message){if(!c)throw new AssertionError(message);}
 public static void main(String[] a) {
  VertexArray vao=new VertexArray();
  var spans=List.of(new Span(3,6),new Span(15,12));
  RegionVertexRanges.select(vao,spans);
  check(RegionVertexRanges.indexCount(vao)==18,"count");
  RegionVertexRanges.draw(vao,4,0,2,0);
  check(GL43.uploads==1 && GL43.draws==1,"first upload and indirect draw");
  check(GL43.lastFirst==3 && GL43.lastSecond==15,"indices not byte offsets");
  check(GL43.current==0,"GL indirect binding restored");
  RegionVertexRanges.select(vao,new ArrayList<>(spans));
  RegionVertexRanges.draw(vao,4,0,2,0);
  check(GL43.uploads==1 && GL43.draws==2,"stable camera must not reupload");
  Utilities.shaderless=false;
  RegionVertexRanges.draw(vao,4,0,2,0);
  check(GL33.legacy==1 && GL43.draws==2,"Oculus retains original GL multi-draw");
  Utilities.shaderless=true; OptimizedRenderer.shadows=true;
  RegionVertexRanges.draw(vao,4,0,2,0);
  check(GL33.legacy==2,"shadow pass retains legacy");
  OptimizedRenderer.shadows=false;
  RegionVertexRanges.select(vao,List.of(new Span(4,12)));
  RegionVertexRanges.draw(vao,4,0,2,0);
  check(GL33.single==1,"single span has direct draw");
  RegionVertexRanges.select(vao,spans);
  RegionVertexRanges.draw(vao,4,0,2,0);
  check(GL43.uploads==2,"changed visibility uploads new commands");
  RegionVertexRanges.remove(vao);
  check(GL43.deletions==1 && !RegionVertexRanges.contains(vao),"GPU lifetime");
  System.out.println("PASS: MDI packing, immutable camera reuse, Oculus/shadow fallback, GL restore, GPU release");
 }
}
"""
}
with tempfile.TemporaryDirectory(prefix="mtr-gpu-indirect-") as dirname:
 root=Path(dirname)
 for name,content in STUBS.items():
  path=root/name
  path.parent.mkdir(parents=True,exist_ok=True)
  path.write_text(content)
 subprocess.run(["javac","-d",dirname,str(SHIPPING),*map(str,root.rglob("*.java"))],check=True)
 subprocess.run(["java","-cp",dirname,"EngineIndirectTest"],check=True)
