"""Exercise the real PIXEL cache against recording texture ownership boundaries, without a GL context."""
from pathlib import Path
import tempfile,subprocess
ROOT=Path(__file__).resolve().parents[1]
STUBS={
'org/mtr/mapping/holder/Identifier.java':'''package org.mtr.mapping.holder;public class Identifier { final String s;public Identifier(String a,String b){s=a+":"+b;}public String toString(){return s;}}''',
'org/mtr/mapping/holder/NativeImageFormat.java':'''package org.mtr.mapping.holder;public class NativeImageFormat {public static NativeImageFormat getAbgrMapped(){return new NativeImageFormat();}}''',
'org/mtr/mapping/holder/NativeImage.java':'''package org.mtr.mapping.holder;import java.util.concurrent.atomic.AtomicInteger;public class NativeImage {
 public static AtomicInteger allocations=new AtomicInteger(),closes=new AtomicInteger(),writes=new AtomicInteger();public int closed;public int w,h;
 public NativeImage(NativeImageFormat f,int w,int h,boolean c){this.w=w;this.h=h;allocations.incrementAndGet();}
 public void setPixelColor(int x,int y,int c){if(!Thread.currentThread().getName().equals("Sillon PIXEL compositor"))throw new AssertionError("CPU conversion on render thread");writes.incrementAndGet();}
 public void close(){if(++closed!=1)throw new AssertionError("double native close");closes.incrementAndGet();}
}''',
'org/mtr/mapping/holder/NativeImageBackedTexture.java':'''package org.mtr.mapping.holder;public class NativeImageBackedTexture {public final NativeImageBackedTexture data=this;public final NativeImage image;public NativeImageBackedTexture(NativeImage i){if(!Thread.currentThread().getName().equals("main"))throw new AssertionError("GL upload on worker");image=i;}public void setFilter(boolean a,boolean b){}public void close(){image.close();}}''',
'org/mtr/mapping/holder/AbstractTexture.java':'''package org.mtr.mapping.holder;public class AbstractTexture {public final NativeImageBackedTexture t;public AbstractTexture(NativeImageBackedTexture t){this.t=t;}}''',
'org/mtr/mapping/holder/TextureManager.java':'''package org.mtr.mapping.holder;import java.util.*;public class TextureManager {public Map<Identifier,AbstractTexture> registered=new HashMap<>();public boolean fail;public void registerTexture(Identifier id,AbstractTexture t){if(fail){fail=false;throw new IllegalStateException("injected upload failure");}registered.put(id,t);}public void destroyTexture(Identifier id){if(org.mtr.mod.render.MainRenderer.referenced.contains(id))throw new AssertionError("released texture still scheduled");AbstractTexture t=registered.remove(id);if(t==null)throw new AssertionError("unowned texture");t.t.close();}}''',
'org/mtr/mapping/holder/MinecraftClient.java':'''package org.mtr.mapping.holder;import java.util.concurrent.*;public class MinecraftClient {static final MinecraftClient c=new MinecraftClient();final TextureManager manager=new TextureManager();final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();public static MinecraftClient getInstance(){return c;}public TextureManager getTextureManager(){return manager;}public void execute(Runnable r){tasks.add(r);}public void drain(){Runnable r;while((r=tasks.poll())!=null)r.run();}}''',
'org/mtr/mapping/mapper/ResourceManagerHelper.java':'''package org.mtr.mapping.mapper;import java.io.*;import java.util.function.*;import org.mtr.mapping.holder.*;public class ResourceManagerHelper {public static String root;public static void readDirectory(String p,BiConsumer<Identifier,InputStream> c){}public static void readResource(Identifier id,Consumer<InputStream> c){try(InputStream i=new FileInputStream(root+"/fabric/src/main/resources/assets/"+id.toString().replace(":","/"))){c.accept(i);}catch(Exception e){throw new RuntimeException(e);}}}''',
'org/mtr/mod/Init.java':'''package org.mtr.mod;public class Init {public static final String MOD_ID="mtr";public static final Log LOGGER=new Log();public static class Log {public void error(String s,Object o){}public void warn(String s,Object o){}}}''',
'org/mtr/mod/render/MainRenderer.java':'''package org.mtr.mod.render;import org.mtr.mapping.holder.Identifier;import java.util.*;public class MainRenderer {public static final Set<Identifier> referenced=new HashSet<>();public static void cancelRender(Identifier i){referenced.remove(i);}}''',
'org/mtr/mod/render/MoreRenderLayers.java':'''package org.mtr.mod.render;import org.mtr.mapping.holder.Identifier;public class MoreRenderLayers {public static int removed;public static void removeFromCache(Identifier i){removed++;}}''',
'AtlasLifecycle.java':'''import org.mtr.mod.render.pixel.*;import org.mtr.mapping.holder.*;import org.mtr.mapping.mapper.ResourceManagerHelper;import java.util.*;
public class AtlasLifecycle {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static PanamSurface.Spec spec(String m){return new PanamSurface.Spec("14","Orly",0x650080,List.of("Orly","Orly"),List.of(m),false,false);}
 static void settle(PixelAtlasCache c)throws Exception{for(int i=0;i<300;i++){Thread.sleep(10);MinecraftClient.getInstance().drain();c.tick();}}
 public static void main(String[] args)throws Exception{
  ResourceManagerHelper.root=args[0];PixelAtlasCache c=PixelAtlasCache.INSTANCE;long now=System.currentTimeMillis();var s=spec("one");
  check(c.get(s,now)==null,"first request asynchronous");check(c.get(s,now)==null,"pending request deduplicated");
  settle(c);var entry=c.get(s,System.currentTimeMillis());check(entry!=null,"one completed upload");check(entry.surface.image==null,"AWT pixels released after worker conversion");
  int allocations=NativeImage.allocations.get();for(int i=0;i<10000;i++)check(c.get(s,System.currentTimeMillis())==entry,"shared persistent page");check(NativeImage.allocations.get()==allocations,"stable frames do not allocate or upload");
  var manager=MinecraftClient.getInstance().getTextureManager();check(manager.registered.size()==1,"one texture owner");
  org.mtr.mod.render.MainRenderer.referenced.add(entry.texture);c.reset();check(manager.registered.isEmpty(),"reload removes all registered textures");check(NativeImage.closes.get()==allocations,"native ownership released once");
  manager.fail=true;c.get(spec("failure"),System.currentTimeMillis());settle(c);check(manager.registered.isEmpty(),"failed upload never published");check(NativeImage.closes.get()==NativeImage.allocations.get(),"failed upload closes pixels");
  c.get(spec("stale"),System.currentTimeMillis());c.reset();settle(c);check(manager.registered.isEmpty(),"old generation cannot publish after reload");check(NativeImage.closes.get()==NativeImage.allocations.get(),"stale worker pixels discarded exactly once");
  for(int i=0;i<30;i++)c.get(spec("queue"+i),System.currentTimeMillis());check(c.diagnostics().contains("pending=8"),"bounded request queue");settle(c);check(manager.registered.size()<=8,"bounded queue publication");
  c.reset();check(manager.registered.isEmpty()&&NativeImage.closes.get()==NativeImage.allocations.get(),"all queued pages released");check(NativeImage.writes.get()>0,"production conversion was executed on compositor thread");
  System.out.println("PASS PIXEL: shared pages, bounded queue, worker conversion, render-thread upload, failed upload, reload race, and exact native ownership");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='pixel-life-') as tmp:
    dest=Path(tmp)
    for name,text in STUBS.items():p=dest/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
    sources=list((ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel').glob('*.java'))
    sources.append(ROOT/'fabric/src/main/java/org/mtr/mod/data/PanamBulletins.java')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(dest),*map(str,sources),*map(str,dest.rglob('*.java'))],check=True)
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(dest),'AtlasLifecycle',str(ROOT)],check=True)
