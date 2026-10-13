"""Compile the production rail pose lookup cache against minimal MTR stubs."""
from pathlib import Path
import subprocess, tempfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java"
RAIL_DELTA=ROOT/"fabric/src/main/java/org/mtr/mod/render/RailColumnDelta.java"
STUBS={
"org/mtr/mod/Init.java":"""
package org.mtr.mod;
public class Init {
 public static final Log LOGGER=new Log();
 public static class Log {public void info(String line,Object... args){}}
 public static int blockPositions;
 public static org.mtr.mapping.holder.BlockPos newBlockPos(double x,double y,double z){
  blockPositions++;return new org.mtr.mapping.holder.BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
 }
}
""",
"org/mtr/core/data/Rail.java":"""
package org.mtr.core.data;
import java.util.List;
public class Rail {
 public final RailMath railMath=new RailMath();
 public java.util.List<String> getStyles(){return List.of("default");}
}
""",
"org/mtr/core/data/RailMath.java":"""
package org.mtr.core.data;
public class RailMath {
 public double minX,maxX,minZ,maxZ;
}
""",
"org/mtr/mapping/holder/BlockPos.java":"""
package org.mtr.mapping.holder;
public class BlockPos {
 public final int x,y,z;
 public BlockPos(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
}
""",
"org/mtr/mapping/holder/ClientWorld.java":"""
package org.mtr.mapping.holder;
public class ClientWorld {
 public final Object data;
 public ClientWorld(Object data){this.data=data;}
}
""",
"org/mtr/mod/render/RailLightFrameCache.java":"""
package org.mtr.mod.render;
import org.mtr.mapping.holder.*;
public class RailLightFrameCache {
 public static int sampled;
 public static int get(ClientWorld world,BlockPos pos){return ++sampled;}
}
""",
"org/mtr/mod/client/MinecraftClientData.java":"""
package org.mtr.mod.client;
import org.mtr.core.data.Rail;
import java.util.*;
public class MinecraftClientData {
 private static final MinecraftClientData INSTANCE=new MinecraftClientData();
 public long networkRevision;
 public final Map<String,RailWrapper> railWrapperList=new LinkedHashMap<>();
 public static MinecraftClientData getInstance(){return INSTANCE;}
 public static class RailWrapper {
  public final String hexId;
  private final Rail rail;
  public RailWrapper(String id,Rail rail){this.hexId=id;this.rail=rail;}
  public Rail getRail(){return rail;}
 }
}
""",
"org/mtr/mod/resource/RailResource.java":"""
package org.mtr.mod.resource;
import org.mtr.mod.render.StoredMatrixTransformations;
public class RailResource{
 public double getModelYOffset(){return .5;}
 public void render(StoredMatrixTransformations matrix,int light){}
}
""",
"org/joml/Matrix4f.java":"""
package org.joml;
public class Matrix4f {
 public Matrix4f rotateY(float a){return this;}
 public Matrix4f rotateX(float a){return this;}
 public Matrix4f rotateZ(float a){return this;}
}
""",
"org/mtr/mod/render/StoredMatrixTransformations.java":"""
package org.mtr.mod.render;
import java.util.function.Consumer;
public class StoredMatrixTransformations {
 public StoredMatrixTransformations(double x,double y,double z){}
 public void add(Consumer<Holder> callback){}
 public static class Holder{
  public void rotateYRadians(float value){}
  public void rotateXRadians(float value){}
  public void rotateZDegrees(float value){}
 }
}
""",
"org/mtr/mod/render/StaticRegionRenderer.java":"""
package org.mtr.mod.render;
import org.mtr.mod.resource.RailResource;
public class StaticRegionRenderer{
 public static int queued,lastLight;
 public StaticRegionRenderer(RenderPipelineMetrics.Family family){}
 public boolean queue(RailResource resource,StoredMatrixTransformations fallback,Object location,double x,double y,double z,org.joml.Matrix4f matrix,int light){queued++;lastLight=light;return true;}
 public void flush(){}
 public void clear(){}
 public void invalidateColumns(java.util.Set<Long> columns){}
}
""",
"org/mtr/mod/render/RenderPipelineMetrics.java":"""
package org.mtr.mod.render;
public class RenderPipelineMetrics {public enum Family{RAIL,TRAIN,DECORATION,OTHER}}
""",
"org/mtr/mod/render/StaticDecorationRegions.java":"""
package org.mtr.mod.render;
public class StaticDecorationRegions {public static void flush(){}public static void clear(){}}
""",
"org/mtr/mod/render/ClassicRailGpuCache.java":"""
package org.mtr.mod.render;
public class ClassicRailGpuCache{
 public static void clear(){}
 public static void invalidateColumns(java.util.Set<Long> columns){}
}
""",
"org/mtr/mod/render/ModelDetailCache.java":"""
package org.mtr.mod.render;
public class ModelDetailCache{public static void clear(){}}
""",
"org/mtr/mod/render/RailGeometrySnapshots.java":"""
package org.mtr.mod.render;
public class RailGeometrySnapshots{public static void clear(){}}
""",
"RailLookupTest.java":"""
import org.mtr.mod.render.RailSectorBatcher;
import org.mtr.mod.resource.RailResource;
import java.lang.reflect.*;
import java.util.*;
public class RailLookupTest {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception {
  RailResource model=new RailResource();
  Field mapField=RailSectorBatcher.class.getDeclaredField("PREPARED");mapField.setAccessible(true);
  Map<?,?> poses=(Map<?,?>)mapField.get(null);
  Field lookupField=RailSectorBatcher.class.getDeclaredField("LOOKUP");lookupField.setAccessible(true);
  Object scratch=lookupField.get(null);
  for(int i=0;i<500;i++)RailSectorBatcher.submit(model,0,0,12,0,0,0,false,100);
  check(poses.size()==1,"repeat submits must not grow pose cache");
  Object originalKey=poses.keySet().iterator().next();
  check(originalKey!=scratch,"mutable scratch must never be inserted as map key");
  int hash=originalKey.hashCode();
  RailSectorBatcher.submit(model,0,0,16,0,0,0,false,100);
  check(poses.size()==2 && originalKey.hashCode()==hash,"stable immutable keys");
  RailSectorBatcher.submit(model,0,0,12,0,0,0,false,120);
  check(poses.size()==2,"light change must not rebuild transforms");
  RailSectorBatcher.clear();
  check(poses.isEmpty(),"world unload cleans pose cache");

  // Production submitVisible entry point, not an unused helper class.
  org.mtr.core.data.RailMath rail=new org.mtr.core.data.RailMath();
  org.mtr.mapping.holder.ClientWorld world=new org.mtr.mapping.holder.ClientWorld(new Object());
  double[] samples={0,0,0,1,12,0,12,1,64,64, 12,0,12,1,24,0,24,1,64,64};
  Class<?> owner=RailSectorBatcher.class;
  Field planField=owner.getDeclaredField("RAIL_PLANS");planField.setAccessible(true);
  org.mtr.mod.render.RailRenderPlanCache<?> plans=
      (org.mtr.mod.render.RailRenderPlanCache<?>)planField.get(null);
  int firstCreated=org.mtr.mod.Init.blockPositions;
  int firstQueues=org.mtr.mod.render.StaticRegionRenderer.queued;
  for(int i=0;i<500;i++)
      check(RailSectorBatcher.submitVisible(rail,model,samples,false,world),"cached batch selected");
  check(plans.builds()==1 && plans.hits()==499,"500 passes must build ONE prepared rail plan");
  check(org.mtr.mod.Init.blockPositions-firstCreated==2,"cached plan must allocate lighting positions only once");
  check(org.mtr.mod.render.StaticRegionRenderer.queued-firstQueues==1000,
        "all visible GPU region selections still submitted every pass");
  check(org.mtr.mod.render.RailLightFrameCache.sampled==1000,
        "lighting must remain dynamic on all 500 draws");
  check(org.mtr.mod.render.StaticRegionRenderer.lastLight==1000,"last sampled lighting used");
  RailSectorBatcher.submitVisible(rail,model,samples,true,world);
  check(plans.builds()==2,"flipped style requires separate immutable plan");
  RailSectorBatcher.submitVisible(rail,model,samples,true,
           new org.mtr.mapping.holder.ClientWorld(new Object()));
  check(plans.builds()==3,"world swap invalidates retained plans");
  RailSectorBatcher.clear();
  check(plans.size()==0,"world unload releases prepared plans");
  System.out.println("PASS: 500 live rail passes -> 1 immutable plan; no repeated pose scans or BlockPos allocations, dynamic lighting and invalidation");
 }
}
"""
}
with tempfile.TemporaryDirectory(prefix="mtr-rail-lookup-") as dirname:
 root=Path(dirname)
 for name,src in STUBS.items():
  f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(src)
 subprocess.run(["javac","-d",dirname,str(SOURCE),str(RAIL_DELTA),
                 str(ROOT/"fabric/src/main/java/org/mtr/mod/render/RailRenderPlanCache.java"),
                 *map(str,root.rglob("*.java"))],check=True)
 subprocess.run(["java","-cp",dirname,"RailLookupTest"],check=True)
