#!/usr/bin/env python3
"""Test real retained GPU sector selection caching, identity and invalidation."""
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/"fabric/src/main/java/org/mtr/mod/render/SectorRenderPlanCache.java"
RENDERER=ROOT/"fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java"
JAVA=r"""
import org.mtr.mod.render.SectorRenderPlanCache;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RetainedSectorTest {
  record Plan(int id,int light) {}
  static void ok(boolean good,String msg){if(!good)throw new AssertionError(msg);}
  public static void main(String[]args) {
    SectorRenderPlanCache<Integer,Plan,Map<Integer,List<Integer>>> cache=new SectorRenderPlanCache<>(2);
    AtomicInteger builds=new AtomicInteger();
    Plan railA=new Plan(1,100),railB=new Plan(2,100);
    List<Plan> same=List.of(railA,railB);
    Map<Integer,List<Integer>> first=cache.resolve(32,same,()->{
       builds.incrementAndGet();
       return Map.of(1,List.of(4,5),2,List.of(6));
    });
    for(int i=0;i<500;i++){
       List<Plan> newList=List.of(railA,railB);
       ok(cache.resolve(32,newList,()->{throw new AssertionError("stable chunk lists rebuilt");})==first,
          "all stable plans share exact GPU page mapping");
    }
    ok(builds.get()==1 && cache.hits()==500,"500 pass reads: one build");
    Plan relit=new Plan(1,110);
    Map<Integer,List<Integer>> changed=cache.resolve(32,List.of(relit,railB),()->{
       builds.incrementAndGet();return Map.of(3,List.of(7));
    });
    ok(changed!=first && builds.get()==2,"light change must invalidate even when IDs match");
    Plan moved=new Plan(1,110);
    cache.resolve(32,List.of(moved,railB),()->{builds.incrementAndGet();return Map.of();});
    ok(builds.get()==3,"transform/geometry rebuild represented by new plan instance");
    cache.resolve(48,List.of(railA),()->{builds.incrementAndGet();return Map.of();});
    cache.resolve(64,List.of(railB),()->{builds.incrementAndGet();return Map.of();});
    ok(cache.size()==2,"retained sectors bounded by capacity");
    ok(cache.invalidateWhere(k->k==48)==1,"column-targeted removal");
    ok(cache.size()==1,"unrelated sector pages still cached");
    cache.clear();
    ok(cache.size()==0,"world unload cleanup");
    System.out.println("PASS: 500 main/shadow passes => 1 sector page list; relight/move, LRU, targeted invalidation");
  }
}
"""
def main():
    src=RENDERER.read_text(encoding="utf-8")
    assert "sectorLists.resolve(region.getKey(),plans" in src, "retained sector list not used in actual flush"
    assert "wanted.putAll(pages)" in src, "actual page selections not submitted from retained plan"
    assert "if(CACHE_SECTOR_LISTS)wantedScratch.clear()" in src, "retained immutable pages may be destructively recycled"
    assert "sectorLists.invalidateWhere(dirty)" in src, "changed rail column must evict cached page selection lists"
    assert "sectorLists.clear()" in src, "resource/world invalidation missing"
    with TemporaryDirectory(prefix="glacier-sectors-") as folder:
        p=Path(folder)/"RetainedSectorTest.java"
        p.write_text(JAVA,encoding="utf-8")
        subprocess.run(["javac","-d",folder,str(CACHE),str(p)],check=True)
        subprocess.run(["java","-cp",folder,"RetainedSectorTest"],check=True)

if __name__=="__main__":main()
