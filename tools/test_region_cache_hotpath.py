"""Compile real region cache; verify steady-state O(0) location updates and revision safety."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "fabric/src/main/java/org/mtr/mod/render/PersistentRegionCache.java"
TEST = r"""
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.mtr.mod.render.PersistentRegionCache;
public class CacheHotpathTest {
  record Piece(int id, int revision) {}
  static final class Gpu implements AutoCloseable {
    final int generation;
    boolean closed;
    Gpu(int generation) { this.generation=generation; }
    public void close(){ if(closed)throw new AssertionError("double GPU free");closed=true; }
  }
  static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
  public static void main(String[] args) {
    var cache=new PersistentRegionCache<String,Piece,Integer,Gpu>(4,4096,4096);
    AtomicInteger mapping=new AtomicInteger(),builds=new AtomicInteger();
    java.util.function.Function<Piece,Integer> locate=p->{mapping.incrementAndGet();return p.id();};
    java.util.function.Function<List<Piece>,Gpu> builder=list->new Gpu(builds.incrementAndGet());
    List<Piece> initial=new ArrayList<>();
    for(int i=0;i<320;i++)initial.add(new Piece(i,0));
    cache.beginFrame();
    Gpu first=cache.get("rail",initial,locate,p->1,builder);
    ok(first!=null&&builds.get()==1,"initial GPU page");
    mapping.set(0);
    for(int frame=0;frame<50;frame++){
      cache.beginFrame();
      Gpu reused=cache.get("rail",initial,locate,p->1,builder);
      ok(reused==first,"stable page rebuilt");
    }
    ok(builds.get()==1,"stable geometry must not reupload");
    ok(mapping.get()==0,"stable page must not rewrite location map");
    cache.beginFrame();
    var relit=new ArrayList<>(initial);
    relit.set(1,new Piece(1,1));
    Gpu second=cache.get("rail",relit,locate,p->1,builder);
    ok(second!=null&&second!=first&&first.closed&&builds.get()==2,
       "light/pose revision must trigger new GPU page");
    cache.beginFrame();
    ok(cache.get("rail",relit,locate,p->1,builder)==second,
       "relit stable page must be retained");
    cache.beginFrame();
    List<Piece> subset=relit.subList(0,100);
    ok(cache.get("rail",subset,locate,p->1,builder)==second,
       "temporary visibility change must not reupload page");
    cache.beginFrame();
    var added=new ArrayList<>(relit); added.add(new Piece(330,0));
    Gpu third=cache.get("rail",added,locate,p->1,builder);
    ok(third!=null&&third!=second&&second.closed&&builds.get()==3,
       "new geometry must extend persistent cache");
    cache.beginFrame();
    ok(cache.get("rail",initial.subList(2,20),locate,p->1,builder)==third,
       "old visible geometry must remain resident");
    cache.close();
    ok(third.closed,"final resource release");
    System.out.println("PASS: no stable-frame location writes, light revisions, visible subsets, new geometry, GPU cleanup");
  }
}
"""
with tempfile.TemporaryDirectory(prefix="mtr-hot-cache-") as tmp:
    p=Path(tmp)/"CacheHotpathTest.java"
    p.write_text(TEST,encoding="utf-8")
    subprocess.run(["javac","-d",tmp,str(SOURCE),str(p)],check=True)
    subprocess.run(["java","-cp",tmp,"CacheHotpathTest"],check=True)
