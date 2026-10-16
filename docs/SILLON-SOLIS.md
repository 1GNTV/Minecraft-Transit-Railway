# Sillon SOLIS ☀️
 
**Sillon** is the project; **SOLIS** is the name of this experimental rendering-engine generation. Forge **1.20.1** and Fabric **1.20.4** are maintained as two branches with the **same** shared renderer sources. Keep the original mod ID `mtr`, `org.mtr.*` Java packages, licences, and existing addon APIs for JCM, MTR France, TJMetro and PANAM.

## Technical scope (source integrated; NO build and NO FPS measurements)

1. **Classic 2D rail scenes**: retained geometry in camera-independent 32-block sectors, live dynamic lighting, cross-track material dispatch; an experimental persistent GPU classic-rail path is available.
2. **3D rail scenes**: spatial-sector lookup, GPU-resident compiled region meshes, range masks, and GL4.3 indirect-command rendering.
3. **GPU-compute culling on static rails**: per-span bounding volumes + compute shader that writes visibility to the `instanceCount` field of an indirect command, then `glMultiDrawElementsIndirect`. CPU still selects sectors initially.
4. **GPU-compute culling on train/rail instances**: compute-shader AABB testing, atomically compacted 76-byte model records, GPU-authored `DrawElementsIndirectCommand.instanceCount`. CPU grouping still exists.
5. **Cross-model GPU material atlas**: `GpuDrivenMaterialAtlas` copies compatible existing vertex/index buffers *on the GPU* to a shared material-specific VAO/index pool without reading mesh data back; heterogeneous model parts are drawn with a single material-scoped `glMultiDrawElementsIndirect`. The compute shader writes per-object visibility to indirect commands; baseVertex selects the mesh and baseInstance selects the model transform, tint and live light data.
6. **Oculus integration**: experimental compute-driven indirect command, preserving the existing Iris/Oculus uniform-array `gl_InstanceID` mapping and shader fallback. It is **not** a cross-material shader pack GPU atlas, and no external shader pack is patched to do bindless texture lookup.

**Architectural limits**: this is not full GPU-side global scene scheduling, nor does it eliminate all CPU draw lists or CPU-side rail visibility. No universal cross-material atlas can batch arbitrary different textures/shaders into one GL draw while preserving shader-pack semantics without deeper shader integration. Dynamic trains, signage, windows, custom addon mixins, JCM/PANAM and shader shadow passes need real-game validation. **Source integration is not evidence of correctness, compilation success or FPS gain.**

## Stable and experimental controls

The new GPU routes are enabled by default when eligible, following the user's request to integrate the system. Experimental shader/driver compatibility is not guaranteed. The exact flags allow A/B isolation without re-compiling; they are read at JVM start:

- `-Dmtr.mega=false` — turn off the MEGA/SOLIS optimization toggles; use the compatible legacy rendering paths.
- `-Dmtr.mega.gpuDriven=false` — turn off GPU-driven indirect/compute paths.
- `-Dmtr.mega.materialAtlas=false` — disable heterogeneous cross-model GPU material atlases.
- `-Dmtr.mega.computeCull=false` — disable compute culling/compaction of compatible TRAIN/RAIL instances.
- `-Dmtr.mega.railCompute=false` — disable GPU culling of static rail index spans.
- `-Dmtr.mega.oculusIndirect=false` — disable optional Iris/Oculus GPU indirect command.
- `-Dmtr.mega.classicGpu=false` — disable the experimental persistent GPU path for classic rails.
- `-Dmtr.mega.classic=false` — disable camera-independent classic rail geometry and cross-track dispatch.
- `-Dmtr.mega.rails=false` — disable the 3D rail retained-scene changes.
- `-Dmtr.engine.classicGpu=false` — independent legacy explicit classic GPU opt-in toggle.

If rendering disappears, has broken UVs or lighting, or crashes, collect the logs **and** rerun the same scene with `-Dmtr.mega.gpuDriven=false` to isolate the GPU backend. If the scene still fails, use `-Dmtr.mega=false`. Store `latest.log`, crash report, game/mod/JCM/Oculus versions, shader-pack settings, driver/GPU details and, if possible, screenshots, Spark profile and FPS/1% lows.

## Versioning and branches

- `work/sillon-solis-forge-1201` → **Sillon SOLIS Forge 1.20.1**, future build file pattern `Sillon-SOLIS-bN-rM-Forge-1.20.1.jar`.
- `work/sillon-solis-fabric-1204` → **Sillon SOLIS Fabric 1.20.4**, future build file pattern `Sillon-SOLIS-bN-rM-Fabric-1.20.4.jar`.

GitHub Actions release-name logic is prepared for these names, but **no build or release was launched** as part of this source update. `[skip ci]` commit messages and source-only new branches avoid automatic builds. Do not distribute SOLIS as stable until both platforms pass compilation and shader/addon smoke tests.

## Future verification checklist

- Forge 1.20.1 and Fabric 1.20.4 Java/mixin compilation
- OpenGL 3.3 fallback and GL4.3 compute shader compilation/link
- Shaderless visuals, Iris/Oculus and Complementary, cascaded shadow passes
- JCM / MTR France / TJMetro / PANAM, vehicle doors and moving parts
- Rail color/preview/editor, model rails, custom resources, lighting updates
- World transition/reload, GL buffer ownership/leaks, render-order/transparency
- Comparable STRATA/SOLIS captures: FPS average/1% low, CPU + GPU time, submitted draw count

Retain previous STRATA/Sillon source branches for rollback.


## GPU dispatch audit (16 October 2026)

This source revision repairs an important performance-integration gap: previously
the cross-model GPU atlas was attempted only **once** for the entire incoming
render batch, even though a real station can contain multiple material families.
One incompatible source made the entire atlas call fail. SOLIS now discovers
**contiguous compatible material segments**, preserves their draw order and
submits only worthwhile heterogeneous groups to the GPU. Unmatched segments
continue to use the preexisting homogeneous instancing path, including
existing shader-pack fallback. A preflight prevents segmentation entirely when
no qualifying mixed-model atlas is present.

- `mtr.mega.railCompute` now defaults to **true** (was **false**): GPU-generated
  visibility commands for opaque static rail spans are enabled where actual
  supported GL4.3/no-shader material conditions hold.
- The GPU model atlas refuses invisible model calls rather than drawing hidden
  instances.
- Instance compute restores the original OpenGL vertex-array binding.
- Oculus GPU-indirect is disabled for shadow cascades. The Oculus shader bridge
  still uses homogeneous shader-compatible uniform arrays rather than a
  universal bindless/mesh-shader material pipeline.
- GPU pipeline diagnostics include atlas, rail, instance and Oculus indirect
  submission counters when `-Dmtr.v4.metrics=true`.

**No baseline FPS values are claimed.** A successfully built JAR only validates
compilation, not GPU driver execution, runtime shader correctness or FPS gain.
The requested *universal*, shader-pack-compatible, all-world GPU scene pipeline
is **not** implemented; there is still CPU-side scene selection and material
bucketing. Claiming otherwise would be misleading.

Triage if you see missing rail geometry or lighting: reproduce with
`-Dmtr.mega.railCompute=false`, then `-Dmtr.mega.materialAtlas=false`,
then `-Dmtr.mega.gpuDriven=false`. Attach `latest.log` and screenshots.
