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
}
""",
"org/mtr/core/data/Rail.java":"""
package org.mtr.core.data;
import java.util.List;
public class Rail {
 public final RailMath railMath=new RailMath();
 public java.util.List<String> getStyles(){return List.of("default");}
 public static class RailMath{
  public double minX,maxX,minZ,maxZ;
 }
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
 public StaticRegionRenderer(RenderPipelineMetrics.Family family){}
 public boolean queue(RailResource resource,StoredMatrixTransformations fallback,Object location,double x,double y,double z,org.joml.Matrix4f matrix,int light){return true;}
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
  System.out.println("PASS: 500 hot-path lookups, stable keys, light independence, exact cleanup");
 }
}
"""
}
with tempfile.TemporaryDirectory(prefix="mtr-rail-lookup-") as dirname:
 root=Path(dirname)
 for name,src in STUBS.items():
  f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(src)
 subprocess.run(["javac","-d",dirname,str(SOURCE),str(RAIL_DELTA),*map(str,root.rglob("*.java"))],check=True)
 subprocess.run(["java","-cp",dirname,"RailLookupTest"],check=True)
