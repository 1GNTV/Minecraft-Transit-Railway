#!/usr/bin/env python3
"""Javac regression for GLACIER rail column diffs + classic rail callback batching."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JAVA=r'''
import org.mtr.mod.render.*;
import org.mtr.mapping.holder.*;
import org.mtr.mod.client.IDrawing;
import java.util.*;

public class GlacierSmoke {
    static void ok(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    static RailColumnDelta.RailState state(Object ref,int rev,double x0,double x1,double z0,double z1){
        return new RailColumnDelta.RailState(ref,rev,x0,x1,z0,z1);
    }
    static long key(int x,int z){return (((long)x)<<32) ^ (z&0xffffffffL);}
    public static void main(String[] args){
        RailColumnDelta delta=new RailColumnDelta(16);
        Object old=new Object(),newRail=new Object();
        Map<String,RailColumnDelta.RailState> first=Map.of(
            "rail",state(old,1,-1,1,-17,-15));
        var added=delta.update(first);
        ok(!added.full() && added.changedRails()==1,"new rail added");
        ok(added.columns().equals(Set.of(key(-1,-2),key(-1,-1),key(0,-2),key(0,-1))),
            "negative chunk bounds");
        var unchanged=delta.update(first);
        ok(!unchanged.full() && unchanged.changedRails()==0 && unchanged.columns().isEmpty(),
            "stable network update must keep meshes");
        var style=delta.update(Map.of("rail",state(old,2,-1,1,-17,-15)));
        ok(!style.full() && style.changedRails()==1,"in-place rail style edit");
        var moved=delta.update(Map.of("rail",state(newRail,2,64,65,48,49)));
        ok(!moved.full() && moved.changedRails()==1 && moved.columns().contains(key(4,3))
            && moved.columns().contains(key(-1,-2)),"move invalidates old and new positions");
        var deleted=delta.update(Map.of());
        ok(!deleted.full() && deleted.changedRails()==1 && deleted.columns().contains(key(4,3)),
            "deleted rail invalidates old position");
        delta.clear();
        ok(delta.update(Map.of("long",state(old,3,-1e9,1e9,-1e9,1e9))).full(),
            "large rail diff must request bounded full fallback");
        delta.clear();

        ClassicRailQuadBatch batch=new ClassicRailQuadBatch(
            new Identifier("rail"),-1,0.065625F,0.00001F,-1,-1,-1,-1);
        for(int i=0;i<257;i++)
            batch.add(i,0,i+0.1,0,i+0.2,1,i+0.3,1,64,64,157);
        batch.flush();
        ok(MainRenderer.CALLBACKS.size()==2,"257 segments must queue two callbacks");
        MainRenderer.CALLBACKS.forEach(cb->cb.accept(new GraphicsHolder(),new Vector3d()));
        ok(IDrawing.COUNT==514,"every quad must keep front and back face");
        ok(IDrawing.LIGHTS.stream().allMatch(x->x==157),"light stays dynamic and unchanged");
        ok(IDrawing.UVS.get(0)[1]==0.1875F,"original stripe UV");
        ok(IDrawing.UVS.get(2)[1]==0.4375F,"adjacent segment texture repeat");
        System.out.println("PASS: changed/unchanged/removed rails + shader-safe bounded classic callbacks");
    }
}
'''
STUBS={
"org/mtr/mapping/holder/Identifier.java":'package org.mtr.mapping.holder; public record Identifier(String value) {}',
"org/mtr/mapping/holder/Direction.java":'package org.mtr.mapping.holder; public enum Direction { UP }',
"org/mtr/mapping/holder/Vector3d.java":'package org.mtr.mapping.holder; public class Vector3d {}',
"org/mtr/mapping/holder/GraphicsHolder.java":'package org.mtr.mapping.holder; public class GraphicsHolder {}',
"org/mtr/mod/render/ClassicRailGpuCache.java":'''package org.mtr.mod.render;
import org.mtr.mapping.holder.Identifier;
public class ClassicRailGpuCache {
 public static boolean queue(Object rail,Identifier texture,int index,double[] coords,int[] lights,float[] stripes,
    float u1,float v1,float u2,float v2,double yOffset,double smallOffset,int color){return false;}
}''',
"org/mtr/mod/render/QueuedRenderLayer.java":'package org.mtr.mod.render; public enum QueuedRenderLayer { EXTERIOR }',
"org/mtr/mod/render/MainRenderer.java":'''package org.mtr.mod.render;
import org.mtr.mapping.holder.*;
import java.util.*;
import java.util.function.BiConsumer;
public class MainRenderer {
 public static final List<BiConsumer<GraphicsHolder,Vector3d>> CALLBACKS=new ArrayList<>();
 public static void scheduleRender(Identifier t,boolean priority,QueuedRenderLayer layer,
                                   BiConsumer<GraphicsHolder,Vector3d> callback) {
  if(priority||layer!=QueuedRenderLayer.EXTERIOR)throw new AssertionError();
  CALLBACKS.add(callback);
 }
}''',
"org/mtr/mod/client/IDrawing.java":'''package org.mtr.mod.client;
import org.mtr.mapping.holder.*;
import java.util.*;
public interface IDrawing {
 List<Integer> LIGHTS=new ArrayList<>();
 List<float[]> UVS=new ArrayList<>();
 int COUNT=0;
 static void drawTexture(GraphicsHolder graphicsHolder,
 double x1,double y1,double z1,double x2,double y2,double z2,
 double x3,double y3,double z3,double x4,double y4,double z4,
 Vector3d offset,float u1,float v1,float u2,float v2,Direction facing,int color,int light) {
  LIGHTS.add(light);UVS.add(new float[]{u1,v1,u2,v2});
  COUNT_HOLDER.value++;
 }
 class COUNT_HOLDER {static int value;}
 static int getCount() {return COUNT_HOLDER.value;}
}'''
}
def main():
    with TemporaryDirectory(prefix="glacier-test-") as temp:
        base=Path(temp)
        files=[]
        for name,source in STUBS.items():
            file=base/name
            file.parent.mkdir(parents=True,exist_ok=True)
            file.write_text(source,encoding="utf-8")
            files.append(file)
        mainjava=base/"GlacierSmoke.java"
        mainjava.write_text(JAVA.replace("IDrawing.COUNT==514","IDrawing.getCount()==514"),encoding="utf-8")
        files.append(mainjava)
        files+= [
            ROOT/"fabric/src/main/java/org/mtr/mod/render/RailColumnDelta.java",
            ROOT/"fabric/src/main/java/org/mtr/mod/render/ClassicRailQuadBatch.java",
        ]
        subprocess.run(["javac","-encoding","UTF-8","-d",str(base/"classes"),*map(str,files)],check=True)
        subprocess.run(["java","-cp",str(base/"classes"),"GlacierSmoke"],check=True)
    rails=(ROOT/"fabric/src/main/java/org/mtr/mod/render/RenderRails.java").read_text()
    batcher=(ROOT/"fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java").read_text()
    regions=(ROOT/"fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java").read_text()
    assert "classicBatch.add(x1, z1, x2, z2, x3, z3, x4, z4, y1, y2, light)" in rails
    assert "if (classicBatch != null) classicBatch.flush();" in rails
    assert "REGIONS.invalidateColumns(changed.columns())" in batcher
    assert "cache.invalidateWhere(page->dirty.test(page.sector()))" in regions
    print("PASS: GLACIER rail pipeline source wiring")

if __name__=="__main__":main()
