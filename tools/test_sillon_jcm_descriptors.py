#!/usr/bin/env python3
"""Probe Sillon/JCM 2.3.0-beta.1 binary Mixin ABI against the real built JAR.

JCM's Java sources inject exact lambda names + captured argument descriptors;
javac may renumber synthetic methods when MTR rendering logic is modified.
This script prints the actual descriptors of relevant compiled entry points.
Set SILLON_JCM_STRICT=true once we establish a working compatibility bridge.
"""
from pathlib import Path
import subprocess,sys,re,os,zipfile

jars=[p for p in Path("forge/build/libs").glob("*-all.jar")]
if len(jars)!=1: raise SystemExit(f"Need exactly one real shaded Forge jar, got {jars}")
jar=jars[0]
def report(cls):
    run=subprocess.run(["javap","-p","-s","-classpath",str(jar),cls],
                       capture_output=True,text=True,check=True)
    lines=run.stdout.splitlines()
    out={}
    for i,l in enumerate(lines):
        if "lambda$" in l and ("(" in l or ";" in l):
            m=re.search(r"(lambda\$[A-Za-z0-9$]+)\s*\(",l)
            if m and i+1<len(lines) and "descriptor:" in lines[i+1]:
                out.setdefault(m.group(1),[]).append(lines[i+1].split("descriptor:",1)[1].strip())
    return out

vehicles=report("org.mtr.mod.render.RenderVehicles")
rails=report("org.mtr.mod.render.RenderRails")
print("SILLON/JCM ABI: RenderVehicles render lambdas:")
for k in sorted(vehicles): 
    if k.startswith("lambda$render$"): print(f"  {k} {vehicles[k]}")
print("SILLON/JCM ABI: RenderRails render lambdas:")
for k in sorted(rails):
    if k.startswith("lambda$render$"): print(f"  {k} {rails[k]}")
def desc(types):
    names={
      "Vector3d":"org/mtr/mapping/holder/Vector3d",
      "Double":"java/lang/Double",
      "PositionAndRotation":"org/mtr/mod/render/PositionAndRotation",
      "VehicleResource":"org/mtr/mod/resource/VehicleResource",
      "VehicleExtension":"org/mtr/mod/data/VehicleExtension",
      "ObjectArrayList":"org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList",
      "ObjectObjectImmutablePair":"org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectObjectImmutablePair",
      "DynamicVehicleModel":"org/mtr/mod/render/DynamicVehicleModel",
      "StoredMatrixTransformations":"org/mtr/mod/render/StoredMatrixTransformations",
      "ObjectArraySet":"org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArraySet",
      "ClientWorld":"org/mtr/mapping/holder/ClientWorld",
      "Rail":"org/mtr/core/data/Rail"
    }
    def d(x):
        return {"int":"I","double":"D","boolean":"Z","int[]":"[I"}.get(x) or "L"+names[x]+";"
    return "("+ "".join(d(t) for t in types)+")V"
expected={
  ("RenderVehicles","lambda$render$5"):desc(["Vector3d","Double","PositionAndRotation","Vector3d","VehicleResource","VehicleExtension","PositionAndRotation","int","int[]","boolean","int","PositionAndRotation"]),
  ("RenderVehicles","lambda$render$12"):desc(["StoredMatrixTransformations","VehicleExtension","int","int[]","PositionAndRotation","ObjectArrayList","boolean","ObjectArrayList","ObjectArrayList","VehicleResource","PositionAndRotation","Vector3d","ObjectObjectImmutablePair","double","int","DynamicVehicleModel"]),
  ("RenderRails","lambda$render$5"):desc(["boolean","ObjectArraySet","ClientWorld","Rail"]),
}
failed=0
for (cls,m),want in expected.items():
    current=(vehicles if cls=="RenderVehicles" else rails).get(m,[])
    match=want in current
    print(("PASS " if match else "INCOMPATIBLE ")+f"JCM 2.3.0-beta.1 {cls}.{m}: expected {want}, found {current}")
    failed+=not match
if failed and os.getenv("SILLON_JCM_STRICT")=="true":
    sys.exit(f"{failed} JCM Mixin injection descriptor(s) incompatible")
print(f"JCM ABI checks: {len(expected)-failed} compatible, {failed} require a bridge")
