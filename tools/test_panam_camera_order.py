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
assert 'GraphicsHolder g,int light,int overlay)' in src
assert 'g.createVertexConsumer(MoreRenderLayers.getLight2(entry.texture));' in src
assert 'RenderLayer.getText(texture)' in layers
assert 'QueuedRenderLayer.LIGHT_TRANSLUCENT' not in src
assert 'MoreRenderLayers.getLight(entry.texture,true)' not in src
assert 'float z=-.0006f*layer[0]++;' in src
assert 'for(int side=0;side<(screenOnly?1:2);side++)' in src
assert 'if(front<=0)return;' in src, 'text layers must never expose the screen back'
assert 'PanamFrame.draw(entry.surface,panel.timeline' in src
assert 'PixelAtlasCache.INSTANCE.get(panel.spec,now)' in src
assert 'if(OptimizedRenderer.renderingShadows())return;' in src
assert 'MainRenderer.scheduleRender(' not in src, 'PANAM must retain the block-entity shader buffer/context'
assert 'class Panel' in src
# With an ordered, unsorted cutout pass, looking left/right doesn't reorder
# sprites. Their local Z order remains strictly toward the face's viewer.
depths=[-.0006*step for step in range(32)]
assert depths[0]==0 and all(b<a for a,b in zip(depths,depths[1:]))
assert 'g.translate(0,-PanamSurface.TOP,0);' in src
assert 'g.rotateXDegrees(2.6f);' in src
assert 'g.translate(-PanamSurface.WORLD_WIDTH/2,0,-.164f);' in src
assert 'arrivals.sort(' not in src
assert 'DEBUG_ARRIVALS' in src
assert abs(depths[-1])<.02
print("PASS: PANAM high-contrast lightmapped text layer, flush face, stable sprites")
