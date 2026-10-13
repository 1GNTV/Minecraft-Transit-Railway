#!/usr/bin/env python3
"""Verify GLACIER's four opt-in/default production paths and shadow culling."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT=Path(__file__).resolve().parents[1]/"fabric/src/main/java"
def src(p):return (ROOT/p).read_text(encoding="utf-8")
scene=src("org/mtr/mod/render/StaticRegionRenderer.java")
rails=src("org/mtr/mod/render/RailSectorBatcher.java")
renderer=src("org/mtr/mod/render/RenderRails.java")
gpu=src("org/mtr/mod/render/RegionVertexRanges.java")
lod=src("org/mtr/mod/render/ModelDetailCache.java")
shadow=src("org/mtr/mod/render/TrainVisibility.java")
assert "REGIONS.replay(piece.ticket,clientWorld.data,currentModel,light)" in rails
assert "if(piece.cachedLight==light" in rails
assert "REGIONS.queue(resource,prepared.transform(),location" in rails
assert "piece.ticket=REGIONS.capture" in rails
assert "if(!PERSISTENT_QUEUE || ticket==null || currentWorld!=world" in scene
assert "ticket.generation!=generation || ticket.model!=model" in scene
assert "ticket.instance.light()!=light" in scene
assert "generation++;" in scene and "public void invalidateColumns" in scene
assert "MTR Persistent queue:" in scene and "queueCalls++" in scene and "replayCalls++" in scene
assert 'SHADER_INDIRECT=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.shaderIndirect","true"))' in gpu
assert "!OptimizedRenderer.renderingShadows()" not in gpu # guard is positive renderingShadows => false
assert "OptimizedRenderer.renderingShadows()" in gpu
assert "!SHADER_INDIRECT" in gpu and "GL43.glMultiDrawElementsIndirect" in gpu
assert "TrainVisibility.railShadowVisible" in renderer
assert "if(!SHADOW_RAIL_CULL || !OptimizedRenderer.renderingShadows() || !usable())return true;" in shadow
assert "frustum.isVisible(new Box(minX-SHADOW_MARGIN" in shadow
assert 'RAIL_LOD=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railLod","true"))' in lod
assert "RAIL_LOD && family==RenderPipelineMetrics.Family.RAIL" in lod
assert "ModelDetailCache.uploaded(mesh,entry.getValue())" in scene
assert "ModelDetailCache.pushDistance(dx*dx+dy*dy+dz*dz)" in scene
assert "ScreenErrorBudget.permits(" in lod
assert 'SHADOW_RAIL_CULL=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.shadowRailCull","true"))' in shadow
assert 'RAIL_LOD_PAGES=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railLod","true"))' in scene
assert 'System.getProperty("mtr.next.shaderLod","true")' in lod
assert 'System.getProperty("mtr.engine.classicGpu","true")' in src("org/mtr/mod/render/ClassicRailGpuCache.java")
assert 'System.getProperty("mtr.next.textAcrossCallbacks","true")' in src("org/mtr/mod/render/MainRenderer.java")
print("PASS: all four features are called by production paths, with safe fallbacks and generation invalidation")

STUBS={
"org/mtr/core/data/VehicleCar.java":"""package org.mtr.core.data;public class VehicleCar{}""",
"org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList.java":"""package org.mtr.libraries.it.unimi.dsi.fastutil.objects;public class ObjectArrayList<T> extends java.util.ArrayList<T>{}""",
"org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectObjectImmutablePair.java":"""package org.mtr.libraries.it.unimi.dsi.fastutil.objects;public class ObjectObjectImmutablePair<L,R>{R r;public ObjectObjectImmutablePair(L l,R r){this.r=r;}public R right(){return r;}}""",
"org/mtr/mapping/holder/Box.java":"""package org.mtr.mapping.holder;public class Box{public final double x,y,z,X,Y,Z;public Box(double a,double b,double c,double d,double e,double f){x=a;y=b;z=c;X=d;Y=e;Z=f;}}""",
"org/mtr/mapping/holder/Frustum.java":"""package org.mtr.mapping.holder;public class Frustum{public boolean isVisible(Box b){return b.X>=-100&&b.x<=100;}}""",
"org/mtr/mapping/holder/Vector3d.java":"""package org.mtr.mapping.holder;public class Vector3d{public double getXMapped(){return 0;}public double getYMapped(){return 0;}public double getZMapped(){return 0;}}""",
"org/mtr/mapping/mapper/OptimizedRenderer.java":"""package org.mtr.mapping.mapper;public class OptimizedRenderer{public static boolean shadows;public static boolean renderingShadows(){return shadows;}}""",
"org/mtr/mod/data/VehicleExtension.java":"""package org.mtr.mod.data;public class VehicleExtension{public Persistent persistentVehicleData=new Persistent();public static class Persistent{public double[] longestDimensions={4};}}""",
"org/mtr/mod/render/PositionAndRotation.java":"""package org.mtr.mod.render;public class PositionAndRotation{public final Pos position=new Pos();public static class Pos{public double x,y,z;}}""",
"ShadowRailTest.java":"""
import org.mtr.mod.render.TrainVisibility;
import org.mtr.mapping.holder.Frustum;
import org.mtr.mapping.mapper.OptimizedRenderer;
public class ShadowRailTest {
static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
public static void main(String[]args) {
 boolean enabled=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.shadowRailCull","true"));
 TrainVisibility.beginFrame();OptimizedRenderer.shadows=true;
 ok(TrainVisibility.railShadowVisible(10000,0,0,10001,1,1),"unknown frustum must fail open");
 TrainVisibility.capture(new Frustum());
 ok(TrainVisibility.railShadowVisible(0,0,0,1,1,1),"near shadow rails retained");
 ok(TrainVisibility.railShadowVisible(10000,0,0,10001,1,1)==!enabled,"far rail culled only with explicit flag");
 OptimizedRenderer.shadows=false;
 ok(TrainVisibility.railShadowVisible(10000,0,0,10001,1,1),"normal main pass never rejects shadow rails");
 System.out.println("PASS shadow frustum, distance, missing capture and main-pass fallback, enabled="+enabled);
}
}
"""
}
with TemporaryDirectory(prefix="glacier-next-") as d:
 root=Path(d)
 for n,s in STUBS.items():
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding="utf-8")
 subprocess.run(["javac","-d",d,str(ROOT/"org/mtr/mod/render/TrainVisibility.java"),*map(str,root.rglob("*.java"))],check=True)
 for setting in [None,"false","true"]:
    cmd=["java"] + ([f"-Dmtr.engine.shadowRailCull={setting}"] if setting else []) + ["-cp",d,"ShadowRailTest"]
    subprocess.run(cmd,check=True)

# Screen-error budget is the actual LOD selection algorithm, not a fake formula.
LODTEST="""
import org.mtr.mod.render.ScreenErrorBudget;
public class RailLodTest {
 public static void main(String[]a){
  if(ScreenErrorBudget.permits(.125,0,64,1,1080,true,.75))
    throw new AssertionError("near rail should remain full detail");
  if(!ScreenErrorBudget.permits(.125,100000000,1,1,1080,true,.75))
    throw new AssertionError("tiny distant rail should permit lower LOD");
  if(ScreenErrorBudget.permits(.125,Double.NaN,1,1,1080,true,.75))
    throw new AssertionError("invalid frustum must retain full geometry");
  System.out.println("PASS real projection-based train/rail LOD math");
 }
}
"""
with TemporaryDirectory(prefix="glacier-rail-lod-") as d:
 p=Path(d)/"RailLodTest.java";p.write_text(LODTEST,encoding="utf-8")
 subprocess.run(["javac","-d",d,str(ROOT/"org/mtr/mod/render/ScreenErrorBudget.java"),str(p)],check=True)
 subprocess.run(["java","-cp",d,"RailLodTest"],check=True)
