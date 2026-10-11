"""Actual Work helpers against recording CPU-image/GPU-buffer boundaries."""
from pathlib import Path
import shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[1]
STUBS = {
"org/mtr/mapping/render/model/Mesh.java": '''package org.mtr.mapping.render.model;
public class Mesh { public int closes; public void close(){if(++closes!=1)throw new AssertionError("double buffer close");} }''',
"org/mtr/mapping/render/object/VertexArray.java": '''package org.mtr.mapping.render.object; public class VertexArray {}''',
"org/mtr/mapping/holder/NativeImageFormat.java": '''package org.mtr.mapping.holder;
public class NativeImageFormat { public static NativeImageFormat getAbgrMapped(){return new NativeImageFormat();} }''',
"org/mtr/mapping/holder/NativeImage.java": '''package org.mtr.mapping.holder;
public class NativeImage {
 public static int creates, closes; public static boolean failAllocate, failCopy;
 public int width,height,closed; public int[][] pixels;
 public NativeImage(NativeImageFormat format,int w,int h,boolean clear){
  if(failAllocate)throw new IllegalArgumentException("allocation");width=w;height=h;pixels=new int[w][h];creates++;
 }
 public int getWidth(){return width;} public int getHeight(){return height;}
 public int getColor(int x,int y){return pixels[x][y];}
 public void setPixelColor(int x,int y,int value){if(failCopy)throw new IllegalArgumentException("copy");pixels[x][y]=value;}
 public void close(){if(++closed!=1)throw new AssertionError("double image close");closes++;}
}''',
"LifecycleTest.java": '''import org.mtr.mod.render.*;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.object.*;
import org.mtr.mapping.holder.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class LifecycleTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static NativeImage image(int w,int h){return new NativeImage(NativeImageFormat.getAbgrMapped(),w,h,false);}
 public static void main(String[] args)throws Exception {
  var buffers=new Mesh();var original=new VertexArray();var alias=new VertexArray();
  GpuMeshOwnership.attach(original,buffers);GpuMeshOwnership.alias(alias,original);
  GpuMeshOwnership.close(original);check(buffers.closes==0);GpuMeshOwnership.close(original);check(buffers.closes==0);
  GpuMeshOwnership.close(alias);check(buffers.closes==1);GpuMeshOwnership.close(alias);check(buffers.closes==1);
  var unrelated=new VertexArray();GpuMeshOwnership.close(unrelated);check(buffers.closes==1);
  // Simulate the actual Cleaner action deterministically; avoid flaky forced-GC timing.
  var collected=new VertexArray();var otherBuffers=new Mesh();GpuMeshOwnership.attach(collected,otherBuffers);
  var field=GpuMeshOwnership.class.getDeclaredField("LEASES");field.setAccessible(true);
  Object lease=((java.util.Map<?,?>)field.get(null)).get(collected);
  ((Runnable)lease).run();check(otherBuffers.closes==1);GpuMeshOwnership.close(collected);check(otherBuffers.closes==1);
  var normal=image(2,2);check(NativeImageCropper.crop(normal,2)==normal);check(normal.closed==0);normal.close();
  var source=image(3,4);source.pixels[1][1]=123;
  var cropped=NativeImageCropper.crop(source,2);check(source.closed==1);check(cropped.getWidth()==2&&cropped.getHeight()==2);check(cropped.getColor(1,1)==123);cropped.close();
  check(NativeImageCropper.crop(null,2)==null);
  var allocation=image(3,3);NativeImage.failAllocate=true;
  try{NativeImageCropper.crop(allocation,2);throw new AssertionError();}catch(IllegalArgumentException expected){}finally{NativeImage.failAllocate=false;}
  check(allocation.closed==1);
  var copy=image(3,3);int closedBefore=NativeImage.closes;NativeImage.failCopy=true;
  try{NativeImageCropper.crop(copy,2);throw new AssertionError();}catch(IllegalArgumentException expected){}finally{NativeImage.failCopy=false;}
  check(copy.closed==1);check(NativeImage.closes==closedBefore+2);
  var queue=new GenerationUploadQueue(2);var uploads=new AtomicInteger();var discarded=new AtomicInteger();long token=queue.generation();
  check(queue.offer(token,uploads::incrementAndGet,discarded::incrementAndGet));check(queue.offer(token,uploads::incrementAndGet,discarded::incrementAndGet));
  check(!queue.offer(token,uploads::incrementAndGet,discarded::incrementAndGet));check(discarded.get()==1&&queue.size()==2);
  check(queue.drain(1)==1);check(uploads.get()==1&&queue.size()==1);queue.invalidate();check(discarded.get()==2&&queue.size()==0);
  check(!queue.offer(token,uploads::incrementAndGet,discarded::incrementAndGet));check(discarded.get()==3);
  token=queue.generation();check(queue.offer(token,uploads::incrementAndGet,discarded::incrementAndGet));queue.drain(2);check(uploads.get()==2);
  // Producer races refresh: all accepted results must upload or be discarded exactly once.
  var racing=new GenerationUploadQueue(16);var accepted=new AtomicInteger();var released=new AtomicInteger();
  Thread producer=new Thread(()->{for(int i=0;i<1000;i++){long g=racing.generation();if(racing.offer(g,released::incrementAndGet,released::incrementAndGet))accepted.incrementAndGet();}});
  producer.start();for(int i=0;i<100;i++)racing.invalidate();producer.join();racing.invalidate();check(released.get()==1000&&racing.size()==0);
  System.out.println("PASS: shared buffer ownership, Cleaner disposal, image transfer/crop/failure cleanup, upload budgets, generation races");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix="mtr-lifecycle-test-") as tmp:
    path = Path(tmp)
    for name, source in STUBS.items():
        target = path / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(source)
    sources = [str(p) for p in path.rglob("*.java")]
    sources += [str(ROOT / f"fabric/src/main/java/org/mtr/mod/render/{name}.java") for name in ["GpuMeshOwnership", "NativeImageCropper", "GenerationUploadQueue"]]
    subprocess.run([shutil.which("javac"), "-d", tmp, *sources], check=True)
    subprocess.run([shutil.which("java"), "-cp", tmp, "LifecycleTest"], check=True)
