#!/usr/bin/env python3
"""NEXT render safety source invariants; does not replace in-game GPU tests."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/"fabric/src/main/java/org/mtr/mod/render"
rails=(root/"RenderRails.java").read_text(encoding="utf-8")
ranges=(root/"RegionVertexRanges.java").read_text(encoding="utf-8")
gpu=(root/"GpuDrivenRailCommands.java").read_text(encoding="utf-8")
assert "renderState == RenderState.FLASHING ? GraphicsHolder.getDefaultLight() : RailLightFrameCache.get(clientWorld, blockPos)" in rails
assert "renderState == RenderState.FLASHING || renderState == RenderState.COLORED ? GraphicsHolder.getDefaultLight()" not in rails
assert 'private static final boolean SHADER_INDIRECT=Boolean.getBoolean("mtr.engine.shaderIndirect");' in ranges
assert "GL33.glMultiDrawElements(mode, ranges.counts, type, ranges.offsets);" in ranges
assert "ranges.gpuModified && !compute" in ranges
bounds=(root/"ModelPartVisibility.java").read_text(encoding="utf-8")
assert "final float[] gpu;" in bounds
assert "return b.gpu;" in bounds
assert "return new float[]{" not in bounds
assert "mtr_Distance" not in gpu
assert "MinecraftClientHelper.getRenderDistance()" not in gpu
assert "float horizontalForward=length(mtr_Forward.xz);" in gpu
assert "horizontalForward>0.35" in gpu
assert "d2>1024.0" in gpu
assert "dot(delta.xz,mtr_Forward.xz)<-radius*horizontalForward" in gpu
assert "words[id*5u+1u]=outside?0u:1u;" in gpu
print("PASS: colored rail lighting, conservative GPU rail culling, shader indirect opt-in, fallback")
