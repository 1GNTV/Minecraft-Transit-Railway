"""Exercise actual optional shadow lookup and expiry/resource token semantics without Minecraft."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    "javax/annotation/Nullable.java": "package javax.annotation; public @interface Nullable {}",
    "net/irisshaders/iris/shadows/ShadowRenderingState.java": """
package net.irisshaders.iris.shadows;
public class ShadowRenderingState {
 public static boolean shadows;
 public static int reads;
 public static boolean areShadowsCurrentlyBeingRendered() {
  reads++;
  if(Boolean.getBoolean("test.readerFailure"))throw new NoClassDefFoundError("unsupported provider");
  return shadows;
 }
}
""",
    "HotspotTest.java": r"""
import org.mtr.mod.render.ShadowPassState;
import org.mtr.mod.resource.*;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import java.lang.reflect.*;
import java.util.*;
public class HotspotTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static WeakExpiryQueue<?> registry()throws Exception {
  Field f=CachedResource.class.getDeclaredField("CACHED_RESOURCES");f.setAccessible(true);
  return (WeakExpiryQueue<?>)f.get(null);
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0 && args[0].equals("disabled")){
   check(ShadowPassState.current()==null);check(ShadowRenderingState.reads==0);
   System.out.println("PASS: shadow switch retains original fallback");return;
  }
  if(Boolean.getBoolean("test.readerFailure")){
   check(ShadowPassState.current()==null);check(ShadowPassState.current()==null);
   check(ShadowRenderingState.reads==1);
   System.out.println("PASS: failed optional provider is disabled once");return;
  }
  check(Boolean.FALSE.equals(ShadowPassState.current()));
  ShadowRenderingState.shadows=true;check(Boolean.TRUE.equals(ShadowPassState.current()));
  ShadowRenderingState.shadows=false;check(Boolean.FALSE.equals(ShadowPassState.current()));
  for(int i=0;i<10000;i++){ShadowRenderingState.shadows=(i&1)==0;check(ShadowPassState.current()==ShadowRenderingState.shadows);}
  check(ShadowRenderingState.reads==10003); // never cache across main/shadow switches
  var q=new WeakExpiryQueue<Object>();Object a=new Object(),b=new Object();int[] visits={0};
  var ta=q.add(a,100);var tb=q.add(b,100);
  q.expire(100,x->{throw new AssertionError("expired at equality");});
  q.expire(101,x->{visits[0]++;return x==a?500:WeakExpiryQueue.DONE;});
  check(visits[0]==2 && q.size()==1);
  q.expire(499,x->{throw new AssertionError("scanned future owner");});
  q.remove(ta);check(q.size()==0);
  var dead=q.add(new Object(),50);dead.clear();dead.enqueue();
  q.expire(10,x->{throw new AssertionError("collected callback");});check(q.size()==0);
  // Equal deadlines and long-lived resources: work only for collected/due entries.
  List<Object> keep=new ArrayList<>();
  for(int i=0;i<50000;i++){Object owner=new Object();keep.add(owner);q.add(owner,Long.MAX_VALUE);}
  for(int i=0;i<100;i++)q.expire(1000,x->{throw new AssertionError("global scan");});
  var shortTicket=q.add(a,999);q.expire(1000,x->{visits[0]++;return WeakExpiryQueue.DONE;});
  check(visits[0]==3 && q.size()==50000);
  int before=registry().size();
  List<CachedResource<Integer>> unloaded=new ArrayList<>();
  for(int i=0;i<20000;i++)unloaded.add(new CachedResource<>(()->1,60000));
  CachedResource.tick();check(registry().size()==before); // constructors do not fill maintenance registry
  int[] builds={0,0};
  var first=new CachedResource<>(()->++builds[0],60000);
  var second=new CachedResource<>(()->++builds[1],60000);
  check(first.getData(false)==1);check(second.getData(false)==null);check(builds[1]==0);
  check(second.getData(true)==1);check(first.getData(true)==1);check(builds[0]==1);
  check(first.hasData() && registry().size()==before+2);
  var empty=new CachedResource<Object>(()->null,60000);check(empty.getData(true)==null);check(registry().size()==before+2);
  var expired=new CachedResource<>(()->++builds[0],-1000);
  check(expired.getData(true)==2);check(!expired.hasData());
  CachedResource.tick();check(registry().size()==before+2);
  check(expired.getData(false)==3);check(!expired.hasData());check(second.getData(false)==1);
  // Deterministically emulate collection of a registered owner, avoiding flaky GC timing.
  Field ticket=CachedResource.class.getDeclaredField("expiryTicket");ticket.setAccessible(true);
  var reference=(java.lang.ref.Reference<?>)ticket.get(first);reference.clear();reference.enqueue();
  CachedResource.tick();check(registry().size()==before+1);
  System.out.println("PASS: live shadow switches, equal/renewed deadlines, collected owners, no idle scans, unloaded registry, TTL and global fetch token");
 }
}
""",
    "AbsentTest.java": """
import org.mtr.mod.render.ShadowPassState;
public class AbsentTest {public static void main(String[] a){if(ShadowPassState.current()!=null)throw new AssertionError();System.out.println("PASS: no Oculus dependency required");}}
"""
}
with tempfile.TemporaryDirectory(prefix="mtr-hotspots-") as tmp:
    base = Path(tmp)
    for name, source in SOURCES.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
    for name in ["render/ShadowPassState", "resource/WeakExpiryQueue", "resource/CachedResource"]:
        path = base / ("org/mtr/mod/" + name + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(ROOT / ("fabric/src/main/java/org/mtr/mod/" + name + ".java"), path)
    subprocess.run(["javac", "-d", tmp] + [str(p) for p in base.rglob("*.java")], check=True)
    for args in [["HotspotTest"], ["-Dmtr.next.shadowState=false", "HotspotTest", "disabled"], ["-Dtest.readerFailure=true", "HotspotTest"]]:
        subprocess.run(["java", "-cp", tmp] + args, check=True)
    (base / "net/irisshaders/iris/shadows/ShadowRenderingState.class").unlink()
    subprocess.run(["java", "-cp", tmp, "AbsentTest"], check=True)
