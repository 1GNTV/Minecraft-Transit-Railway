#!/usr/bin/env python3
"""STRATA source-level compatibility contract: no extra local captures.

The compiled JAR receives an additional descriptor/bytecode verification.
The full JVM+JCM+Oculus runtime test must still be performed by a player.
"""
from pathlib import Path
src=Path("fabric/src/main/java/org/mtr/mod/render/RenderRails.java").read_text()
engine=Path("fabric/src/main/java/org/mtr/mod/render/strata/StrataRailEngine.java").read_text()
render=src.split("public static void render() {",1)[1].split("public static boolean isHoldingRailRelated",1)[0]
order=[
 "final MinecraftClient minecraftClient",
 "final ClientWorld clientWorld",
 "final ClientPlayerEntity clientPlayerEntity",
 "final ObjectArrayList<Function<OcclusionCullingInstance, Runnable>> cullingTasks",
 "final Vector3d cameraPosition",
 "final Vec3d camera",
 "final boolean holdingRailRelated",
 "final ObjectArrayList<Rail> railsToRender",
 "final ObjectArraySet<Rail> hoverRails",
]
loc=[render.index(i) for i in order]
assert loc==sorted(loc), "JCM captures changed order"
assert "editorOverlay" not in render
prefix=render[:loc[-1]]
assert prefix.count("final boolean shadowPass") == 1
scope=prefix.index("final boolean shadowPass")
end=prefix.index("\n\t\t}",scope)
assert loc[7]<scope<end<loc[8], "Extra locals leaked into JCM capture sites"
assert "StrataRailEngine.candidates(cameraPosition)" in render
assert "StrataRailEngine.visible(railWrapper, shadowPass)" in render
assert "StrataRailEngine.finishPass(shadowPass)" in render
assert "occlusionCullingInstance ->" in render, "JCM real culling callback removed"
assert "railsToRender.forEach(rail ->" in render, "JCM real rail rendering callback removed"
assert render.count("renderRailStats(")==4, "JCM inject ordinals changed"
assert "railSpatialIndex.query" in engine
assert "TrainVisibility.railShadowVisible" in engine
assert "shadowPass" not in render[render.index("StrataRailEngine.finishPass(shadowPass);") + len("StrataRailEngine.finishPass(shadowPass);"):render.index("// Ghost rails (when holding brush)")]
print("PASS STRATA: rail selector isolated; 3 JCM rail-stat capture sites and performance paths retained")

# STRATA/JCM RenderVehicles: the actual model callback must not capture an
# extra visualCar boolean or JCM fails with InvalidInjectionException.
vehicles=Path("fabric/src/main/java/org/mtr/mod/render/RenderVehicles.java").read_text()
models=vehicles.split("vehicleResource.iterateModels(",1)[1].split("\n\t\t\t\t\t\t});",1)[0]
assert "final boolean visualCarHere = StrataVehicleVisibility.visible();" in models
assert "if (visualCar)" not in models and "&& visualCar," not in models
assert "StrataVehicleVisibility.push(visualCar)" in vehicles
assert "StrataVehicleVisibility.pop(previousStrataVisibility)" in vehicles
print("PASS STRATA train model callbacks: no extra visualCar lambda capture")

# New STRATA cache fast path must be opt-in per immutable sector page.
regions=Path("fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java").read_text()
cache=Path("fabric/src/main/java/org/mtr/mod/render/PersistentRegionCache.java").read_text()
assert "CACHE_SECTOR_LISTS && STRATA_IMMUTABLE_MASK" in regions
assert "sectorPlanScratch.clear()" in regions
assert "immutableVisible && entry.verifiedImmutableLists.contains(visible)" in cache
assert "verifiedImmutableLists.clear()" in cache
print("PASS STRATA immutable selection fast path: protected by immutable sector cache and invalidation")
