"""Check a dense station retains unique static region membership across frames."""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"standalone-engine/src/main/java/dev/igntv/mtrengine/core/PersistentRegionCache.java"
TEST=r"""
import java.util.*;
import dev.igntv.mtrengine.core.PersistentRegionCache;
public class DenseRegionTest {
 record Part(int id){}
 static class GPU implements AutoCloseable {
  boolean closed;
  public void close() {if(closed)throw new AssertionError("double release");closed=true;}
 }
 static List<Part> parts(int start,int count){
  ArrayList<Part> a=new ArrayList<>();
  for(int i=0;i<count;i++)a.add(new Part(start+i));
  return a;
 }
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 public static void main(String[] args) {
  var cache=new PersistentRegionCache<String,Part,Integer,GPU>(64,2000,2000);
  int[] builds={0};
  java.util.function.Function<List<Part>,GPU> make=s->{builds[0]++;return new GPU();};
  List<Part> a=parts(0,300),b=parts(300,100),c=parts(400,50);
  cache.beginFrame();GPU first=cache.get("station",a,Part::id,v->1,make);
  check(first!=null && builds[0]==1,"initial 300-piece page");
  cache.beginFrame();GPU second=cache.get("station",b,Part::id,v->1,make);
  check(second!=null && first.closed && builds[0]==2,"new 100-piece membership");
  cache.beginFrame();check(cache.get("station",a,Part::id,v->1,make)==second,
     "old 300 pieces remain in the compiled page");
  cache.beginFrame();GPU third=cache.get("station",c,Part::id,v->1,make);
  check(third!=null && second.closed && builds[0]==3,"50-piece extension");
  cache.beginFrame();check(cache.get("station",b,Part::id,v->1,make)==third,
     "300+100+50 unique members must survive; no per-frame membership wipe");
  check(builds[0]==3,"unexpected dense region recompile");
  cache.close();check(third.closed,"GPU owner cleanup");
  System.out.println("PASS: 450 distinct stable scene elements, no pathological membership resets, exact GPU release");
 }
}
"""
with tempfile.TemporaryDirectory(prefix="mtr-dense-region-") as dirname:
 path=Path(dirname)/"DenseRegionTest.java"
 path.write_text(TEST)
 subprocess.run(["javac","-d",dirname,str(SOURCE),str(path)],check=True)
 subprocess.run(["java","-cp",dirname,"DenseRegionTest"],check=True)
