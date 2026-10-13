#!/usr/bin/env python3
"""Sillon AURORA rail world-light stage regression guard.

This validates the *actual* production scheduling callbacks, not an unused helper.
Lighting levels remain baked into per-vertex lightmap UVs; the exterior
layer binds the proper world-lit render state for Oculus/Forge.
"""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"fabric/src/main/java/org/mtr/mod/render"
region=(p/"StaticRegionRenderer.java").read_text()
classic=(p/"ClassicRailGpuCache.java").read_text()
rails=(p/"RenderRails.java").read_text()
main=(p/"MainRenderer.java").read_text()
assert 'System.getProperty("sillon.aurora.railExteriorLighting","true")' in region
assert 'System.getProperty("sillon.aurora.railExteriorLighting","true")' in classic
assert "RAIL_EXTERIOR_LIGHTING && family==RenderPipelineMetrics.Family.RAIL" in region
assert "QueuedRenderLayer.EXTERIOR : QueuedRenderLayer.TEXT,(holder,offset)->{" in region
assert "RAIL_EXTERIOR_LIGHTING ? QueuedRenderLayer.EXTERIOR : QueuedRenderLayer.TEXT,(holder,offset)->{" in classic
assert ".set(VertexAttributeType.UV_LIGHTMAP,VertexAttributeSource.VERTEX_BUFFER)" in region
assert ".set(VertexAttributeType.UV_LIGHTMAP,VertexAttributeSource.VERTEX_BUFFER)" in classic
assert "baked.vertices.forEach(v->v.light=light)" in region
assert "int convertedLight=Utilities.exchangeLightmapUVBits(lights[i])" in classic
assert "RailLightFrameCache.get(clientWorld,piece.lightPosition())" in (p/"RailSectorBatcher.java").read_text()
assert "RailLightFrameCache.get(clientWorld, blockPos)" in rails
assert "renderLayer = MoreRenderLayers.getExterior(key)" in main
assert "case EXTERIOR:" in main
# TEXT is deliberately a no-RenderLayer state: cached opaque rails MUST NOT use it by default.
assert "default:\n\t\t\t\t\t\t\trenderLayer = null;" in main
print("PASS: both GPU rail paths use EXTERIOR shader/lightmap layer, dynamic light retained, JVM rollback available")
