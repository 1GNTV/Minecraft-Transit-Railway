#!/usr/bin/env python3
"""Regression guard: restore high-FPS Sillon defaults without removing addons."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=lambda f:(root/"fabric/src/main/java/org/mtr/mod/render"/f).read_text(encoding="utf-8")
rail=source("RailGeometrySnapshots.java")
visibility=source("TrainVisibility.java")
instancing=source("InstancedModelRenderer.java")
init=(root/"fabric/src/main/java/org/mtr/mod/InitClient.java").read_text(encoding="utf-8")
loader=(root/"fabric/src/main/java/org/mtr/mod/client/CustomResourceLoader.java").read_text(encoding="utf-8")
assert 'System.getProperty("mtr.next.regions", "true")' in rail
assert 'System.getProperty("mtr.next.visibility", "true")' in visibility
assert 'System.getProperty("mtr.next.shadowDistanceCull", "true")' in visibility
assert 'OptimizedRenderer.renderingShadows()' in visibility
assert 'MinecraftClientHelper.getRenderDistance() * 16D + 64D' in visibility
assert 'return frustum.isVisible(new Box(x0,y0,z0,x1,y1,z1))' in visibility
assert 'final boolean shaderless = Utilities.canUseCustomShader();' in instancing
assert 'VertexAttributeType[] INSTANCE_ATTRIBUTES' in instancing
assert "JcmPresetRecovery.tick()" in init
assert "CustomResourceLoader.hasLoadedInitialResources()" in init
assert 'INITIAL_RESOURCES_LOADED = true;' in loader
print("PASS: Fabric region batching + conservative train/shadow culling + instancing hotpath, addon loaders retained")
