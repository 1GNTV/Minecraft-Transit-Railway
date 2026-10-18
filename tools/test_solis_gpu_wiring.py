#!/usr/bin/env python3
"""Source-level SOLIS GPU wiring assertions. This is NOT a GPU or FPS test."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
src=lambda name:(ROOT/'fabric/src/main/java/org/mtr/mod/render'/name).read_text(encoding='utf-8')
# Compute pipelines exist but remain opt-in pending driver, shader and scene validation.
assert 'Boolean.getBoolean("mtr.mega.railCompute")' in src('GpuDrivenRailCommands.java')
assert 'Boolean.getBoolean("mtr.mega.computeCull")' in src('GpuDrivenInstanceRenderer.java')
assert 'Boolean.getBoolean("mtr.mega.materialAtlas")' in src('GpuDrivenMaterialAtlas.java')
assert 'GpuDrivenRailCommands.cull(' in src('RegionVertexRanges.java')
assert 'GL43.glMultiDrawElementsIndirect(' in src('RegionVertexRanges.java')
assert 'GpuDrivenMaterialAtlas.sameMaterial(' in src('InstancedModelRenderer.java')
assert 'GpuDrivenMaterialAtlas.worthwhile(' in src('InstancedModelRenderer.java')
assert 'drawExisting(calls,ordinary,shaderless)' in src('InstancedModelRenderer.java')
assert '!c.mtr$visible()' in src('GpuDrivenMaterialAtlas.java')
assert 'GL43.glMultiDrawElementsIndirect(' in src('GpuDrivenMaterialAtlas.java')
assert 'GL43.glDispatchCompute(' in src('GpuDrivenMaterialAtlas.java')
assert 'GL43.glDrawElementsIndirect(' in src('GpuDrivenInstanceRenderer.java')
assert 'previousVAO' in src('GpuDrivenInstanceRenderer.java')
assert '&& !org.mtr.mapping.mapper.OptimizedRenderer.renderingShadows()' in src('OculusGpuIndirectCommand.java')
assert 'OculusGpuIndirectCommand.report();' in src('RenderPipelineMetrics.java')
print('PASS: SOLIS GPU wiring and flags (not an actual driver or FPS test)')
