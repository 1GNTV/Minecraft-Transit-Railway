# MTR Forge 1.20.1: V3 rendering experiment

This branch is based on the measured V2 render pipeline and targets repeated shader activation and OpenGL state calls.

## New rendering fast paths

- Per-material-batch validation of the currently bound OpenGL shader program.
- When MTR changes only MODEL_VIEW_MATRIX for an instance of a known active shader, upload only that uniform instead of repeating full ShaderInstance.apply() (which can also trigger Oculus framebuffer and texture bindings).
- Deduplication of identical GL VAO binds scoped to a known MTR batch. Invalidated at each shader/material batch transition.
- Existing V2 temporary-matrix-buffer and generic-attribute caches remain.

## Safety switches
- `-Dmtr.v3.fastUniforms=false`: use stock ShaderInstance.apply() for each MTR model instead of fast direct uniform updates.
- `-Dmtr.v3.cacheVertexArrays=false`: always bind VAOs as in stock MTR.

Switches are separate, allowing precise isolation of graphics and compatibility bugs.

## Testing required
1. Launch Forge 1.20.1 with the complete modpack and Oculus enabled. Verify transparency, glowing, matrix transforms, moving trains, camera transitions and PIDS.
2. Repeat without shader pack and with F3+T reload.
3. Compare identical fixed-camera positions using sparkc profiles: ShaderInstance.apply, nglDrawElements, glBindFramebuffer, glBindVertexArray and FPS/1%-lows.
4. Re-test with `-Dmtr.v3.fastUniforms=false` if graphics break.
5. Do not declare 100+ FPS without an apples-to-apples measured benchmark.

The V3 experiment does **not yet implement GPU mesh instancing, persistent merged geometry, or PIDS texture atlasing**. Those need separate model and compatibility work and must not be represented as implemented.

### Why no naïve draw call fusion?
BatchManager groups by material but VertexArray draws have different geometry, VAOs and per-instance matrices.
Combining them into a single draw requires a compatible vertex format/shader and buffer representation; glDrawElements loops cannot simply be skipped.
