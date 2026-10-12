"""Compile the real Oculus GLSL map bridge without loading Oculus or GL classes."""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"fabric/src/main/java/org/mtr/mod/render/OculusShaderMapBridge.java"
STUBS={
"org/mtr/mod/render/InstanceShaderSource.java":"""
package org.mtr.mod.render;
public class InstanceShaderSource {public record Pair(String vertex,String fragment){}}
""",
"org/mtr/mod/render/OculusInstanceRenderer.java":"""
package org.mtr.mod.render;
public class OculusInstanceRenderer {
 public static int invalidations;
 public static InstanceShaderSource.Pair prepare(String v,String f){return null;}
 public static void invalidatePrograms(){invalidations++;}
}
""",
"MapBridgeTest.java":r"""
import java.util.*;
import org.mtr.mod.render.*;
public class MapBridgeTest {
 enum Stage { VERTEX,GEOMETRY,TESS_CONTROL,TESS_EVAL,FRAGMENT }
 static void ok(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 public static void main(String[] args){
  Map<Stage,String> original=Map.of(Stage.VERTEX,"original-v",Stage.FRAGMENT,"original-f");
  ok(OculusShaderMapBridge.patch(original)==original,"disabled bridge must return same original object");
  var transformed=OculusShaderMapBridge.tryPatch(original,(v,f)->
    new InstanceShaderSource.Pair(v+"-patched",f+"-patched"));
  ok(transformed!=original,"original must be copied on success");
  ok(transformed.get(Stage.VERTEX).equals("original-v-patched")
      && transformed.get(Stage.FRAGMENT).equals("original-f-patched"),"paired shader mutation");
  ok(original.get(Stage.VERTEX).equals("original-v"),"source map immutable");
  ok(OculusInstanceRenderer.invalidations==1,"invalidate program uniform locations");
  var unsupported=Map.of(Stage.VERTEX,"v",Stage.GEOMETRY,"g",Stage.FRAGMENT,"f");
  ok(OculusShaderMapBridge.tryPatch(unsupported,(v,f)->{throw new AssertionError();})==unsupported,
     "geometry shaders are not supported");
  var single=Map.of(Stage.VERTEX,"v");
  ok(OculusShaderMapBridge.tryPatch(single,(v,f)->{throw new AssertionError();})==single,
     "incomplete map must remain unchanged");
  ok(OculusShaderMapBridge.tryPatch(original,(v,f)->null)==original,
     "unsupported pack must retain original shader");
  ok(OculusShaderMapBridge.tryPatch(null,(v,f)->null)==null,"null input");
  System.out.println("PASS: optional shader map, immutable copy, paired stages, null/geometry fallback");
 }
}
"""
}
with tempfile.TemporaryDirectory(prefix="mtr-oculus-map-") as name:
 root=Path(name)
 for relative,src in STUBS.items():
  p=root/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(src)
 subprocess.run(["javac","-d",name,str(SOURCE),*map(str,root.rglob("*.java"))],check=True)
 subprocess.run(["java","-cp",name,"MapBridgeTest"],check=True)
