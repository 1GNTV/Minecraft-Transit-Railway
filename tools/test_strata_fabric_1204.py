#!/usr/bin/env python3
"""Validate Fabric 1.20.4 port keeps STRATA and JCM 2.2.5 source hooks."""
from pathlib import Path
import json
P=Path("fabric/src/main")
meta=json.loads((P/"fabric.mod.template.json").read_text())
assert meta["id"]=="mtr" and "mtr.strata.performance.mixins.json" in meta["mixins"]
m=json.loads((P/"resources/mtr.strata.performance.mixins.json").read_text())
assert m["package"]=="org.mtr.mod.mixin"
for name in ("VertexAttributeStateMixin","OptimizedRendererPassMixin","BatchInstancingMixin",
             "RenderCallMetricsMixin","MaterialPropertiesMixin","ModelDetailUploadMixin"):
    assert name in m["client"],name
for name in m["client"]:
    assert (P/"java/org/mtr/mod/mixin"/(name+".java")).exists(),name
rails=(P/"java/org/mtr/mod/render/RenderRails.java").read_text()
vehicles=(P/"java/org/mtr/mod/render/RenderVehicles.java").read_text()
assert "public static void render() {" in rails
assert "renderRailStats(" in rails
assert "railsToRender.forEach(rail ->" in rails
assert "vehicleResource.iterateModels(carNumber" in vehicles
assert "vehicleResource.queue(storedMatrixTransformations" in vehicles
assert "StrataVehicleVisibility.push(visualCar)" in vehicles
cache=(P/"java/org/mtr/mod/data/VehicleExtension.java").read_text()
assert "StrataVehiclePositionCache" in cache
print("PASS Fabric 1.20.4 STRATA sources + JCM v2.2.5 callback anchors")
