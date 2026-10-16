# Sillon GPU-driven compute passes (experimental source-only)

Status: **integrated into Forge 1.20.1 and Fabric 1.20.4 source branches**, **not compiled, tested, benchmarked, or visually validated**. Do not call this a finished universal GPU-driven renderer. No release JAR has been built by this change.

## Actual implementations

### GPU-computed train/rail-model instance visibility (GL 4.3 shaderless)

`GpuDrivenInstanceRenderer` installs a compute shader for selected opaque, depth-writing, homogeneous `VertexArray` groups from `InstancedModelRenderer` with conservative retained mesh bounds. A workgroup reads the existing 76-byte MTR instance records from an input SSBO, checks eight clip-space AABB corners against the current projection * view * instance transform, atomically compacts accepted instances into a **separate output GPU buffer**, and updates the `instanceCount` field of a resident `DrawElementsIndirectCommand`. A memory barrier precedes `glDrawElementsIndirect`. The CPU does **not** read the compacted instance count back. The original per-model VAO, index buffer, texture/material state, and per-instance color, overlay and light remain in use. The existing GL33 instanced renderer is the fallback. Per-VAO ring pages retain GPU fences and own the new input + command buffers; resource reload clears shader program state.

Eligibility: shaderless rendering, OpenGL 4.3, opaque non-cutout-hack depth-writing geometry, stored mesh AABB, TRAIN/RAIL family, grouping size at least 48 by default, not a shadow cascade. CPU-side call selection and grouping remain; this does not yet replace the entire train submission architecture.

### GPU-computed static rail drawing commands (GL 4.3 shaderless)

`StaticRegionRenderer` now retains sector-relative 3D AABBs for each baked rail mesh range, and registers the immutable 16-block sector origin with `RegionVertexRanges`. `GpuDrivenRailCommands` uses a second compute shader to test the rail range's conservative world-space bounds against distance + camera-forward constraints, and sets each resident indirect command's `instanceCount` to 0 or 1 in GPU memory. `glMultiDrawElementsIndirect` consumes those commands after the compute barrier. GPU command buffers are restored to all-visible status when switching back to non-compute draws (Oculus, shadow passes or fallback).

This is a **fine-grained GPU command culling layer over the preexisting CPU-selected regions**, not a fully GPU-resident world-level rail scheduler. Legacy 2D classic rails still use the newly retained sector geometry and cross-track CPU/vertex-consumer batching. They are **not** yet part of the GPU compute culler.

### Compatibility and resource lifetime

- No legacy JCM/mixin method signatures were removed or changed.
- Unsupported GL, GL4ES, missing mesh AABBs, small batches, shader packs/Oculus, shader-shadow passes and translucent models retain the existing renderer.
- A failed compute preflight should disable that compute path, leaving ordinary rendering in use.
- GPU scratch buffers are allocated per existing instancing page; static rail bounds buffers are owned by the existing indexed-range lifecycle.
- These claims describe code paths only. Visual parity and actual error handling **still require runtime tests**.

## Flags (JVM arguments; evaluated on game startup)

Both new compute shaders are **OFF by default pending real-world validation**:

- `-Dmtr.mega.computeCull=true` — opt into compute-selected/compacted instances for TRAIN/RAIL model groups, shaderless GL 4.3 only.
- `-Dmtr.mega.railCompute=true` — opt into GPU command culling for opaque static 3D rail pages, shaderless GL 4.3 only.
- `-Dmtr.mega.computeMinInstances=48` — train-model group size threshold (8…1024).
- `-Dmtr.mega.railComputeMin=16` — rail indirect-command count threshold (2…4096).
- `-Dmtr.mega.gpuDriven=false` — master switch, also disables the preexisting indirect static-region drawing route.
- `-Dmtr.mega.instances=false` — disables MEGA instance improvements and new GPU instance culling.
- `-Dmtr.mega.rails=false` and `-Dmtr.mega.classic=false` — rail scene/classic scene rollback.

## Outstanding work, not claimed as done

1. Native **Oculus shader-compatible GPU-owned train instance/material pipeline**, with shader patch integration and real shader-pack validation. Current Oculus rendering keeps its original uniform-array instancing/ordinary draw fallback.
2. Global cross-material mesh atlas / merged GPU vertex-and-index storage so **different** train parts and different rail materials can be emitted from a much smaller set of indirect calls.
3. GPU-only broad-phase culling and fully persistent world-level command lists so camera movement no longer requires CPU rail collection/submission.
4. GPU treatment of classic textured 2D rails with dynamic light/shader parity.
5. In-game real-world draw-count/FPS/1% low benchmarks and complete Forge/Fabric addon compatibility tests.

## Mandatory verification before claiming success

Compile both target versions, exercise no-shader and Oculus/Complementary paths, verify MTR France + JCM + TJMetro, shadow cascades, moving trains/doors, lighting, rail changes and world reload. For benchmarks compare identical worlds, GPU workloads and draw counts before/after with `-Dmtr.v4.metrics=true`. Never add hypothetical FPS to actual benchmark data.

Source-only changes were committed using `[skip ci]`; no manual build or benchmark was requested or launched.
