"""Exercise the actual cache against a recording GL stub, with no OpenGL context."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
GL = '''package org.lwjgl.opengl;
public final class GL33 {
 public static int calls;
 public static void glVertexAttrib2f(int i,float x,float y) { calls++; }
 public static void glVertexAttrib3f(int i,float x,float y,float z) { calls++; }
 public static void glVertexAttrib4f(int i,float x,float y,float z,float w) { calls++; }
 public static void glVertexAttribI2i(int i,int x,int y) { calls++; }
}'''
TEST = '''import org.lwjgl.opengl.GL33;
import org.mtr.mod.render.GlVertexAttributeCache;
public final class CacheTest {
 static void write() { GlVertexAttributeCache.attrib4f(1,1,1,1,1); }
 static void expect(int count) {
  if(GL33.calls!=count) throw new AssertionError("expected "+count+", got "+GL33.calls);
 }
 public static void main(String[] args) {
  if(args.length>0) {
   GlVertexAttributeCache.begin(true); write(); write(); expect(2);
   GlVertexAttributeCache.end();
   System.out.println("PASS: attribute-cache kill switch"); return;
  }
  write(); write(); expect(2); // Never cache external renderers.
  GlVertexAttributeCache.begin(true);
  write(); write(); expect(3);
  GlVertexAttributeCache.invalidate(); write(); expect(4); // Material boundary.
  GlVertexAttributeCache.invalidate(1,1); write(); expect(5); // Enabled array draw.
  GlVertexAttributeCache.attribI2i(1,1,1); expect(6); // Integer vs float.
  GlVertexAttributeCache.attribI2i(1,1,1); expect(6);
  GlVertexAttributeCache.attrib2f(2,0.0f,1); expect(7);
  GlVertexAttributeCache.attrib2f(2,-0.0f,1); expect(8); // Preserve signed zero.
  GlVertexAttributeCache.end(); write(); expect(9);
  GlVertexAttributeCache.begin(false); write(); write(); expect(11); // Oculus fallback.
  GlVertexAttributeCache.end();
  GlVertexAttributeCache.begin(true); write(); expect(12); // New pass is fresh.
  GlVertexAttributeCache.begin(true); write(); write(); expect(14); // Nested fallback.
  GlVertexAttributeCache.end(); write(); write(); expect(16); // Parent invalidated.
  GlVertexAttributeCache.end();
  try { GlVertexAttributeCache.begin(true); write(); throw new RuntimeException(); }
  catch(RuntimeException expected) {} finally { GlVertexAttributeCache.end(); }
  write(); write(); expect(19); // Failure cleanup.
  System.out.println("PASS: external state, material boundaries, enabled arrays, types, signed zero, shaders, nested passes, failures");
 }
}'''
with tempfile.TemporaryDirectory(prefix='mtr-cache-test-') as temp:
    work = Path(temp)
    stub = work / 'org/lwjgl/opengl/GL33.java'
    stub.parent.mkdir(parents=True)
    stub.write_text(GL)
    main = work / 'CacheTest.java'
    main.write_text(TEST)
    source = ROOT / 'fabric/src/main/java/org/mtr/mod/render/GlVertexAttributeCache.java'
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('Java JDK required')
    subprocess.run([javac, '-d', str(work), str(stub), str(source), str(main)], check=True)
    subprocess.run([java, '-cp', str(work), 'CacheTest'], check=True)
    subprocess.run([java, '-Dmtr.v4.attributeCache=false', '-cp', str(work), 'CacheTest', 'disabled'], check=True)
