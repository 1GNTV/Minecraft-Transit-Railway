# MTR Forge 1.20.1 — V4 architecture and performance audit (in progress)

## Scope and baseline

**Baseline:** `perf/forge-1201` (V2), not the slower `perf/forge-1201-v3` experiment.
**Target:** heavy stations, including stationary camera/no nearby trains; same behavior, assets, shaders, signs, railway geometry, and inter-mod compatibility.
**User measurements (same world, different branch tests):** V2 ~65–75 FPS with shaders and ~100–125 FPS without. V3 ~60–70 FPS with shaders and worse without. These measurements are approximate, **not** validated comparable 1% lows.
**Goal:** sustained FPS above V2, with a stretch goal of +100 FPS versus original stock MTR only where the hardware-bound bottleneck permits it. No guaranteed FPS figure.

This is the **initial architectural audit**, not a claim that every source file and dependency was line-by-line reviewed. Git tree inspection of the V2 `fabric` subtree enumerated 4,785 files (372 Java); the Forge source setup copies shared Java source from `fabric/src/main/java`. The separate MTR France Addon fork needs compatibility profiling.

## Findings verified against V2 source

1. **Global rail enumeration per rendered frame** — `RenderRails.render()` walks `MinecraftClientData.railWrapperList.values()`, creating an occlusion task per rail and scheduling occlusion for the full list. This is O(total known rails), even if most rails are beyond view distance. The existing `ClientSpatialIndex` indexes *stations and platforms only*, not railway geometry. The occlusion worker's latest-frame queues do not eliminate the per-frame enumeration and task allocation on the client thread.
   - Files: `render/RenderRails.java`, `client/MinecraftClientData.java`, `client/ClientSpatialIndex.java`, `render/WorkerThread.java`.
2. **Rail geometry is sampled repeatedly at render submission** — V2 `RailGeometryCache` retains 10 doubles per rail segment, eliminating some `RailMath.render()` computations; however `renderRailStandard()` still traverses segments to compute light/texture values and enqueue closure-based draw commands every frame. Custom model pieces also recreate rotations (`atan2`) and `StoredMatrixTransformations` per visible segment.
   - Files: `client/RailGeometryCache.java`, `render/RenderRails.java`, `render/MainRenderer.java`.
3. **Render queue remains rebuilt every frame** — `MainRenderer` queues callbacks grouped by stage/layer/material; `RenderCallbackPool` reuses backing lists, but it does not cache the actual final geometry or reuse persistent GPU buffers. Optimized model rendering is delegated to MTR mapping classes, which are external build dependencies.
   - Files: `render/MainRenderer.java`, `render/RenderCallbackPool.java`, `resource/OptimizedRendererWrapper.java`, `fabric/build.gradle`.
4. **Vanilla PIDS platform discovery repeated on V2** — auto-configured PIDS call `findStation()`/`findClosePlatform()` per render. V3 includes a 40-tick weak-key platform-ID cache; this is a useful isolated candidate for V4, provided world/sync invalidation is handled and the visual output remains correct.
   - File: `render/RenderPIDS.java`; compare V2 and V3.
5. **Signs and route displays do repeated block-state and layout work** — `RenderRouteBase.getTextureNumber()` checks neighboring blocks and `RenderRailwaySign` resolves textures/route info each frame. Some output is already covered by `DynamicTextureCache`, but that does not remove upstream repeated selection/layout calls.
   - Files: `render/RenderRouteBase.java`, `render/RenderRailwaySign.java`, `client/DynamicTextureCache.java`.
6. **Culling is partly bounded, but still schedules global work** — `BoundedOcclusionCullingInstance` caps massive AABB scans; `CachedCullingDataProvider` avoids repeated opacity reads during one culling pass. These are useful V2 defenses; they do not reduce the global input set to the worker.
   - Files: `render/BoundedOcclusionCullingInstance.java`, `render/CachedCullingDataProvider.java`.
7. **Special edit-mode hotspot** — while holding rail tools, `RenderRails` additionally scans a 33 × 33 × 33 block cube (35,937 positions) in the render path. Isolate behind an incremental/region-cached approach in a separate targeted change; it is not necessarily the cause of normal idle-station FPS loss.
8. **Build architecture matters** — `forge/build.gradle` copies the mod's Java source from `fabric` at setup time. Java behavior changes for Forge generally belong in the shared source tree, with the Forge mappings/loader glue and shader compatibility assessed separately. `Minecraft-Mappings-forge-1.20.1` is an external dependency, not included in the 372 Java count.

## V4 strategy — order of experiments

### A. Instrument before optimizing (safe, no output changes)
Add opt-in per-frame counters for:
- Total rails, camera-distance candidates, occlusion jobs enqueued, actual visible rails;
- Rail sample cache hit/miss, segment callback count, custom model transformation count;
- Queued render callbacks by layer/material and approximate cache occupancy;
- CPU durations for rail selection, render submission, culling enqueue, sign/PIDS work;
- Shadow pass separate from main pass, plus shader/no-shader distinction.

Collect Spark CPU profiles, actual FPS and 1%-low frame times at fixed camera/camera settings. Never infer GPU speedup from CPU stack traces alone.

### B. Conservative rail spatial index (first meaningful V4 optimization)
Introduce an *independent* `RailSpatialIndex` keyed to rail AABBs, rebuilt **only on static rail-data sync**, not on dynamic vehicle snapshot sync.
- Query a conservative camera-centered X/Z region based on configured render distance and model/rail expansion; include long rail bounding boxes crossing that region.
- Keep a safe overflow bucket for very large rail extents and stable de-duplication.
- Preserve existing rail draw state, shadow semantics, special ghost rails, and the final per-segment/frustum tests.
- Add a debug fallback switch `-Dmtr.v4.railIndex=false` to use V2's original full scan.
- Report candidate count versus total known rails in a large world.
- Critically, do not introduce false negatives for curved rails, long models, or rail brushes.

### C. Reuse immutable segment descriptors
Cache static per-rail/style/repeat-interval segment metadata (positions, UVs, optional fixed rotations and bounds). Keep dynamic light, color, signals, rail-tool flash, and active materials updated at draw time. Invalidate after rail network sync, style change, reload, or generation/version mismatch.

A persistent GPU geometry cache is **not** the same change: only prototype that separately once the CPU-vs-GPU profile justifies it. Renderer state must coexist with Oculus, shaders, translucency, resource packs, and multiple render passes. It must not silently skip dynamic content.

### D. Reduce repeated sign/PIDS work
- Port the well-isolated V3 auto-PIDS cache only after tying it to the network-data generation and reload lifecycle; do not copy V3's shader/VAO shortcuts.
- Cache route-sign static layout and neighboring-block-count calculations with explicit invalidations for neighboring changes, block entity NBT, network sync, language/resource reload.
- Keep arrival countdowns, scrolling, multilingual switches and blinking dynamic.

### E. Controlled culling scheduling
Use spatial candidates to reduce per-frame culling input first. Then test bounded work and generation-tagged results, discarding stale jobs without blocking the render thread. Avoid reading mutable client worlds unsafely on new threads. Visibility cache behavior must remain correct after movement, door/block updates and world unload.

### F. GPU batching only after profiling
Experimental immutable *default-rail* geometry chunk batches, then possibly instance-compatible repeating model parts. Must preserve render order, texture/light/shader semantics and render stage boundaries; not a blanket GL draw-call fusion. Compare main thread vs GPU limited cases separately.

## Exclusions / reasons not to copy V3 wholesale

- V3's fast ShaderInstance uniform uploads and VAO deduplication reduced some GL work but **did not improve end-to-end FPS** in the user's test.
- Such low-level OpenGL state bypasses can conflict with Oculus, material state and render-pass changes.
- V4 should use the V2 baseline and cherry-pick only measured changes that survive both shader and shaderless A/B tests.

## Required acceptance matrix

1. Station idle, trains moving, inside train, and while editing rails.
2. Same view position, distance, world, resource pack, shader pack and Oculus/modpack as V2 baseline.
3. No shaders + shaders; test F3+T, dimension/world change, rail edit, signs/PIDS and lights, translucent surfaces.
4. Compare FPS median and 1%-low; Spark per-frame/call count; watch memory allocation and GPU draw count if available.
5. Compile Forge 1.20.1, smoke-test with MTR France Addon and resource packs before labelling any V4 build successful.
6. Roll back each optimization independently using feature switches; verify output equivalence.

## Audit still to do

- Full file-by-file review of remaining Java classes, especially `render`, `block`, `client`, `resource`, and `data`, plus generated/pack assets relevant to rendering.
- Trace MTR's external `Minecraft-Mappings` / `Transport-Simulation-Core` implementation of `OptimizedRenderer`, `BatchManager` and the actual GL call costs; this cannot be inferred from wrappers alone.
- MTR France Addon interaction (Copycat block entities, station signs, invisible platforms), including past crash diagnosis and compatibility.
- Obtain/reopen actual Spark profile binaries before claiming any bottleneck percentage.

**Status:** documentation/plan only, no V4 FPS optimization or benchmark claimed yet.
