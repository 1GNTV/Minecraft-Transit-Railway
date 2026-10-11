"""Compile the actual diagnostics helpers against recording boundaries; no GL context."""
from pathlib import Path
import shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[1]
STUBS = {
"org/lwjgl/opengl/GL.java": """package org.lwjgl.opengl;
public class GL {
 public static int capabilityCalls;
 public static Capabilities getCapabilities(){capabilityCalls++;return new Capabilities();}
 public static class Capabilities {public boolean OpenGL33=!Boolean.getBoolean("test.unsupported");}
}""",
"org/lwjgl/opengl/GL33.java": """package org.lwjgl.opengl;
public class GL33 {
 public static final int GL_TIMESTAMP=1,GL_QUERY_RESULT_AVAILABLE=2,GL_QUERY_RESULT=3;
 public static int generated, reads, timestamps, availability;
 public static boolean ready;
 public static int glGenQueries(){return ++generated;}
 public static void glQueryCounter(int id,int target){timestamps++;}
 public static int glGetQueryObjecti(int id,int target){availability++;return ready?1:0;}
 public static long glGetQueryObjectui64(int id,int target){
  if(!ready)throw new AssertionError("Blocking read attempted");
  reads++;return id*1000L;
 }
}""",
"org/mtr/mod/Init.java": """package org.mtr.mod;
public class Init {public static final Log LOGGER=new Log(); public static class Log{public void info(String s,Object... x){}}}""",
"org/mtr/mapping/mapper/OptimizedRenderer.java": """package org.mtr.mapping.mapper;
public class OptimizedRenderer {public static boolean shadows; public static boolean renderingShadows(){return shadows;}}""",
"org/mtr/mapping/render/tool/Utilities.java": """package org.mtr.mapping.render.tool;
public class Utilities {public static boolean custom=true;public static boolean canUseCustomShader(){return custom;}}""",
"DiagnosticsTest.java": """import org.mtr.mod.render.*;
import org.lwjgl.opengl.*;
import org.mtr.mapping.mapper.OptimizedRenderer;
import java.lang.reflect.*;
public class DiagnosticsTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static long count(int cat,String field,int family)throws Exception {
  Field f=RenderPipelineMetrics.class.getDeclaredField("stats");f.setAccessible(true);
  Object s=((Object[])f.get(null))[cat];
  Field v=s.getClass().getDeclaredField(field);v.setAccessible(true);
  return family<0?v.getLong(s):((long[])v.get(s))[family];
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0) {
   GpuPassTimer.end(GpuPassTimer.begin(0));check(GL33.generated==0);
   if(args[0].equals("disabled"))check(GL.capabilityCalls==0);
   System.out.println("PASS: "+args[0]);return;
  }
  for(int i=0;i<40;i++)GpuPassTimer.end(GpuPassTimer.begin(0));
  check(GL33.generated==64 && GL33.timestamps==64 && GL33.reads==0);
  GL33.ready=true;GpuPassTimer.end(GpuPassTimer.begin(1));
  check(GL33.reads==8 && GL33.generated==64); // Four ready pairs, bounded polling.
  check(count(0,"gpuSamples",-1)==4);
  RenderPipelineMetrics.Family old=RenderPipelineMetrics.pushQueueFamily(RenderPipelineMetrics.Family.TRAIN);
  try {check(RenderPipelineMetrics.captureFamily()==RenderPipelineMetrics.Family.TRAIN);}
  finally {RenderPipelineMetrics.restoreQueueFamily(old);}
  check(RenderPipelineMetrics.captureFamily()==RenderPipelineMetrics.Family.OTHER);
  RenderPipelineMetrics.queued(RenderPipelineMetrics.Family.TRAIN);
  RenderPipelineMetrics.begin(true);
  RenderPipelineMetrics.drawing(RenderPipelineMetrics.Family.TRAIN);
  RenderPipelineMetrics.draw(6);
  OptimizedRenderer.shadows=true;
  RenderPipelineMetrics.begin(false);
  RenderPipelineMetrics.drawing(RenderPipelineMetrics.Family.RAIL);
  RenderPipelineMetrics.draw(3);
  RenderPipelineMetrics.matrixShaderPath();
  RenderPipelineMetrics.end();
  OptimizedRenderer.shadows=false;
  RenderPipelineMetrics.draw(9); // Nested pass must restore parent family/category.
  RenderPipelineMetrics.end();
  check(count(0,"draws",1)==2 && count(0,"triangles",1)==5 && count(0,"queued",1)==1);
  check(count(3,"draws",0)==1 && count(3,"matrixShaderPaths",-1)==1);
  RenderPipelineMetrics.draw(3);
  check(count(0,"draws",1)==2); // No counting external draws.
  System.out.println("PASS: bounded GPU queries, nonblocking reads, capability fallback, family scopes, nested main/shadow counters");
 }
}"""
}
with tempfile.TemporaryDirectory(prefix="mtr-diagnostics-") as tmp:
    base=Path(tmp)
    for path, content in STUBS.items():
        p=base/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
    for name in ["GpuPassTimer","RenderPipelineMetrics"]:
        p=base/"org/mtr/mod/render"/(name+".java");p.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy(ROOT/"fabric/src/main/java/org/mtr/mod/render"/(name+".java"),p)
    subprocess.run(["javac","-d",str(base)]+[str(p) for p in base.rglob("*.java")],check=True)
    for options in [
        ["-Dmtr.v4.metrics=true","-Dmtr.v4.gpuMetrics=true","DiagnosticsTest"],
        ["DiagnosticsTest","disabled"],
        ["-Dmtr.v4.gpuMetrics=true","-Dtest.unsupported=true","DiagnosticsTest","unsupported"],
    ]:
        subprocess.run(["java","-cp",str(base)]+options,check=True)

