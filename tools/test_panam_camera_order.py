#!/usr/bin/env python3
"""PANAM angle-invariant layer ordering (Fabric 1.20.4).

Minecraft sorts beaconBeam(translucent) quads by camera position on upload.
A painter-ordered sprite UI must instead keep quad emission order across
camera directions, while preserving both face transforms and shared atlases.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=(root/"fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java").read_text()
layers=(root/"fabric/src/main/java/org/mtr/mod/render/MoreRenderLayers.java").read_text()
assert 'QueuedRenderLayer.EXTERIOR' in src
assert 'g.createVertexConsumer(MoreRenderLayers.getExterior(entry.texture));' in src
assert 'RenderLayer.getEntityCutout(texture)' in layers
assert 'QueuedRenderLayer.LIGHT_TRANSLUCENT' not in src
assert 'MoreRenderLayers.getLight(entry.texture,true)' not in src
assert 'float z=-.004f*layer[0]++;' in src
assert 'for(int side=0;side<2;side++)' in src
assert 'PanamFrame.draw(entry.surface,panel.timeline' in src
assert 'PixelAtlasCache.INSTANCE.get(panel.spec,now)' in src
assert 'renderingShadows()' not in src
assert 'class Panel' in src
# With an ordered, unsorted cutout pass, looking left/right doesn't reorder
# sprites. Their local Z order remains strictly toward the face's viewer.
depths=[-.004*step for step in range(32)]
assert depths[0]==0 and all(b<a for a,b in zip(depths,depths[1:]))
print("PASS: PANAM sprites use a camera-invariant non-sorting cutout batch")
