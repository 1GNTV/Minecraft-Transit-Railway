#!/usr/bin/env python3
"""Compile production immutable rail hash-key classes without Minecraft dependencies.

Ensures hash memoization preserves the record-era value semantics and keeps
the scene-plan cache valid across equivalent keys, revisions and invalidation.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java"
RAILS=ROOT/"fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java"
CACHE=ROOT/"fabric/src/main/java/org/mtr/mod/render/ScenePlanCache.java"

def class_body(source,name):
    marker="private static final class "+name+" {"
    start=source.index(marker)
    opening=source.index("{",start)
    depth=0
    for i in range(opening,len(source)):
        if source[i]=="{":depth+=1
        elif source[i]=="}":
            depth-=1
            if depth==0:return source[start:i+1]
    raise ValueError("unterminated production class "+name)

JAVA=r"""
import java.util.*;
import org.mtr.mod.render.ScenePlanCache;
public class GlacierHashTest {
    static final class RailResource {}
    static final class OptimizedModelWrapper {}
    private record Sector(int x,int y,int z){}
    private static final class MatrixKey {
        final float[] values; final int hash;
        MatrixKey(float... values){this.values=values.clone();hash=Arrays.hashCode(this.values);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other) {
            return other instanceof MatrixKey m && Arrays.equals(values,m.values);
        }
    }
    __LOCATION__
    __PLAN__
    __FRAGMENT__
    __SELECTION__
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        RailResource resource=new RailResource();
        Location a=new Location(resource,13,-0.0,32,0.3f,-0.0f,1.2f);
        Location b=new Location(resource,13,-0.0,32,0.3f,-0.0f,1.2f);
        check(a!=b && a.equals(b) && b.equals(a),"equal rail poses");
        check(a.hashCode()==b.hashCode(),"stable rail pose hash");
        check(!a.equals(new Location(resource,13,+0.0,32,0.3f,-0.0f,1.2f)),
              "signed zero in position must remain significant");
        check(!a.equals(new Location(resource,13,-0.0,32,0.3f,+0.0f,1.2f)),
              "signed zero in rotation must remain significant");
        for(int i=0;i<100000;i++)check(a.hashCode()==b.hashCode(),"stable hot lookup hash");

        Sector sec=new Sector(-1,0,2);
        PlanKey first=new PlanKey(sec,a);
        PlanKey equivalent=new PlanKey(new Sector(-1,0,2),b);
        check(first.equals(equivalent) && first.hashCode()==equivalent.hashCode(),
              "different but equivalent region plans");
        PlanKey differentSector=new PlanKey(new Sector(0,0,2),a);
        check(!first.equals(differentSector),"sector identity");
        HashSet<PlanKey> keySet=new HashSet<>();
        keySet.add(first);
        check(keySet.contains(equivalent),"hash-based plan retrieval");
        check(!keySet.contains(differentSector),"distinct columns");
        ScenePlanCache<PlanKey,Integer,Object> plans=new ScenePlanCache<>(100);
        Object value=new Object();
        check(plans.resolve(first,5,()->value)==value,"first build");
        check(plans.resolve(equivalent,5,()->{throw new AssertionError("plan rebuilt");})==value,
              "equal new PlanKey must hit existing scene");
        check(plans.invalidateWhere(k->k.sector().equals(sec))==1,"targeted invalidation");
        Object next=plans.resolve(equivalent,5,Object::new);
        check(next!=value,"invalidated scene must rebuild");

        OptimizedModelWrapper model=new OptimizedModelWrapper();
        Fragment fragmentA=new Fragment(a,model,3);
        Fragment fragmentB=new Fragment(b,model,3);
        check(fragmentA.equals(fragmentB) && fragmentA.hashCode()==fragmentB.hashCode(),
              "fragment signatures retain equals semantics");
        MatrixKey matA=new MatrixKey(1,0,0,1);
        MatrixKey matB=new MatrixKey(1,0,0,1);
        Selection selA=new Selection(model,fragmentA,matA,100,3);
        Selection selB=new Selection(model,fragmentB,matB,100,3);
        check(selA.equals(selB) && selA.hashCode()==selB.hashCode(),
              "deep selection hashes must remain equivalent");
        check(!selA.equals(new Selection(model,fragmentB,matB,101,3)),
              "light change invalidates compiled page");
        HashSet<Selection> compiled=new HashSet<>();
        compiled.add(selA);
        check(compiled.contains(selB),"compiled page reusable via hash lookup");
        check(!compiled.contains(new Selection(model,fragmentB,matB,100,4)),
              "geometry chunk change invalidates compiled page");
        System.out.println("PASS: memoized production rail/plan/fragment/selection hashes and invalidation");
    }
}
"""

def main():
    source=SRC.read_text(encoding="utf-8")
    rails=RAILS.read_text(encoding="utf-8")
    java=(JAVA.replace("__LOCATION__",class_body(rails,"Location"))
             .replace("__PLAN__",class_body(source,"PlanKey"))
             .replace("__FRAGMENT__",class_body(source,"Fragment"))
             .replace("__SELECTION__",class_body(source,"Selection")))
    assert "railPlanKeys.get(location)" in source
    assert "railPlanKeys.clear()" in source
    assert "scenePlans.resolveLazy(planKey(sector,location)" in source
    with TemporaryDirectory(prefix="glacier-keyhash-") as tmp:
        f=Path(tmp)/"GlacierHashTest.java"
        f.write_text(java,encoding="utf-8")
        subprocess.run(["javac","-d",tmp,str(CACHE),str(f)],check=True)
        subprocess.run(["java","-cp",tmp,"GlacierHashTest"],check=True)

if __name__=="__main__":main()
