"""Exercise real rail selection cache ownership/budgets without a GL context."""
from pathlib import Path
import shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[1]
TEST = r'''import org.mtr.mod.render.RailSectorCache;
import java.util.*;
public class RailCacheTest {
 static int builds, closes;
 static class R implements AutoCloseable { boolean closed; public void close(){check(!closed);closed=true;closes++;} }
 static R build(){builds++;return new R();}
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  var a=List.of(1,2,3,4);var b=List.of(1,2,3,5);
  var cache=new RailSectorCache<String,Integer,R>(2,20,12);
  cache.beginFrame();check(cache.get("a",a,10,RailCacheTest::build)==null);check(builds==0);
  cache.beginFrame();R first=cache.get("a",a,10,RailCacheTest::build);check(first!=null);check(builds==1);
  // Updating a sector already queued this frame must not close its resident resource.
  check(cache.get("a",b,10,RailCacheTest::build)==null);check(!first.closed);
  check(cache.get("b",a,10,RailCacheTest::build)==null);
  cache.beginFrame();check(cache.get("a",a,10,RailCacheTest::build)==first);
  R second=cache.get("b",a,10,RailCacheTest::build);check(second!=null);check(builds==2);
  check(cache.get("c",a,10,RailCacheTest::build)==null);check(cache.size()==2);check(!first.closed&&!second.closed);
  cache.beginFrame();check(cache.get("a",b,10,RailCacheTest::build)==null);check(first.closed);
  // Drop an unused sector to respect metadata capacity; never exceed residency budget.
  check(cache.get("c",a,10,RailCacheTest::build)==null);check(second.closed);check(cache.size()==2);
  cache.beginFrame();R changed=cache.get("a",b,10,RailCacheTest::build);check(changed!=null);
  check(cache.get("c",a,10,RailCacheTest::build)==null);check(builds==3); // one construction/frame
  cache.beginFrame();check(cache.get("c",a,10,RailCacheTest::build)!=null);check(cache.residentVertices()==20);
  check(cache.get("huge",a,13,RailCacheTest::build)==null);check(cache.get("tiny",List.of(1),1,RailCacheTest::build)==null);
  cache.close();check(closes==builds);check(cache.residentVertices()==0&&cache.size()==0);cache.close();check(closes==builds);
  var failures=new RailSectorCache<String,Integer,R>(2,20,12);
  failures.beginFrame();failures.get("a",a,10,RailCacheTest::build);
  failures.beginFrame();try{failures.get("a",a,10,()->{throw new IllegalArgumentException("test");});throw new AssertionError();}catch(IllegalArgumentException expected){}
  failures.beginFrame();check(failures.get("a",a,10,RailCacheTest::build)==null); // no retry storm
  failures.get("a",b,10,RailCacheTest::build);
  failures.beginFrame();check(failures.get("a",b,10,RailCacheTest::build)!=null);failures.close();
  System.out.println("PASS: stable selections, invalidation, pinned resources, one build/frame, bounded residency, failure fallback, exact disposal");
 }
}'''
with tempfile.TemporaryDirectory(prefix="mtr-rail-test-") as tmp:
    path = Path(tmp)
    (path / "RailCacheTest.java").write_text(TEST)
    subprocess.run([shutil.which("javac"), "-d", tmp, str(ROOT / "fabric/src/main/java/org/mtr/mod/render/RailSectorCache.java"), str(path / "RailCacheTest.java")], check=True)
    subprocess.run([shutil.which("java"), "-cp", tmp, "RailCacheTest"], check=True)
