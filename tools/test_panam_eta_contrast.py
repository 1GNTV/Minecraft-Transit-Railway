#!/usr/bin/env python3
"""PANAM contrast and ETA compatibility checks against MTR's PIDS renderer."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
panam=(root/"fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java").read_text()
mtr=(root/"fabric/src/main/java/org/mtr/mod/render/RenderPIDS.java").read_text()
layers=(root/"fabric/src/main/java/org/mtr/mod/render/MoreRenderLayers.java").read_text()
assert 'ArrivalsCacheClient.INSTANCE.requestArrivals(panel.platforms)' in panam
assert 'ArrivalsCacheClient.INSTANCE.requestArrivals(platformIds)' in mtr
assert 'arrivals.sort(' not in panam
assert 'getMillisOffset()' in panam and 'getMillisOffset()' in mtr
assert 'if(!entity.getPlatformIds().isEmpty())panel.platforms.addAll(entity.getPlatformIds());' in panam
assert 'GraphicsHolder.getDefaultLight()' in panam
assert 'QueuedRenderLayer.LIGHT_2' in panam
assert 'MoreRenderLayers.getLight2(entry.texture)' in panam
assert 'RenderLayer.getText(texture)' in layers
assert 'mtr.panam.debugArrivals' in panam
print("PASS: PANAM uses MTR arrival order, lightmapped text and per-platform diagnostics")
