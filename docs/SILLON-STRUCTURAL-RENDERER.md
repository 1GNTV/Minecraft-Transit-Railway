# Sillon structural rail-engine pass — source integration only

This is a **source-only**, uncompiled/unbenchmarked renderer change on Forge 1.20.1 and Fabric 1.20.4. Do not publish a stable JAR or claim FPS improvements before validating the two engines in game.

## Paths integrated in this pass

1. **Classic rail renderer**: `ClassicRailSceneCache` retains full track geometry independent of camera direction, spatially groups samples in 32x32-block sectors, and applies exactly the existing range and forward-facing tests each render pass. World light is looked up live via the existing `RailLightFrameCache`. Oversized tracks fall back to `renderWithinRenderDistance`. Animated/editor rails continue to use the vanilla callback path.
2. **Cross-track classic rail dispatch**: `ClassicRailRenderQueue` collects compatible opaque rail quads from *multiple tracks* during each MTR render pass into one rendering callback per material/layout combination, preserving the same `IDrawing.drawTexture` two-face vertices, UVs, direction, and light values. A per-pass upper bound prevents unbounded memory growth. The separate legacy opt-in `ClassicRailGpuCache` takes precedence when enabled; its shader/light parity is still unverified and remains disabled by default.
3. **Custom 3D rail spatial scene**: `RailSectorBatcher` partitions camera-independent prepared 3D rail pieces into immutable 32-block sectors. When camera moves, it checks each sector's radial bounding rectangle before its individual pieces, still preserving dynamic lighting and existing region replay/GPU residency.
4. **GPU indirect rendering improvement**: `RegionVertexRanges` now sorts **only depth-writing, non-translucent, non-cutout-hack** static page spans by index prior to adjacency coalescing. This can reduce commands submitted via its *already existing* OpenGL 4.3 Multi-Draw Indirect renderer. All other materials retain their exact original order. Shader/GL fallback remains in place.

## Important limitation

A **complete new GPU-driven train renderer** with global GPU-owned culling and shared multi-material mesh atlases has **not been implemented** here. Existing trains still use the already-present GL instancing/Oculus bridge and train visibility. Existing static rail regions already supported Multi-Draw Indirect before this pass. This patch must never be described as implementing a new universal GPU-driven renderer that draws all trains/rails in a handful of calls. That is an additional architectural project requiring shader integration, GPU-managed transforms, real hardware testing and fallback verification.

## Rollback switches

All flags are read at JVM startup:

- `-Dmtr.mega=false`: disable all MEGA integration and retain prior paths.
- `-Dmtr.mega.rails=false`: turn off the retained 3D rail-scene path.
- `-Dmtr.mega.classic=false`: disable new classic spatial cache **and** cross-track dispatch.
- `-Dmtr.sillon.classicDispatch=false`: disable only cross-track dispatch, retaining the prior per-track 256-quad batching.
- `-Dmtr.mega.gpuDriven=false`: disable indirect GPU draws from static region range renderer while preserving its proven GL multi-draw fallback.
- `-Dmtr.mega.opaqueSpanSort=false`: retain previous visible range ordering.
- `-Dmtr.mega.instances=false`: disable MEGA wider Oculus instancing.

The preexisting `-Dmtr.engine.classicGpu=true` experiment explicitly enables persistent classic GPU pages at the expense of the proven vertex-consumer shader/light path. This is **not** enabled by the new changes.

## Required validation before release

- Compiler checks: Forge 1.20.1 and Fabric 1.20.4.
- Visual parity on baseline 2D rails, 3D rails, crossings, cable cars, one-way tracks, Oculus/Complementary and shadow cascades.
- Addons: JCM presets, MTR France rails, TJMetro vehicle and rail hooks, custom resources, PANAM PIDS.
- Trains: cars, bogies, translucent windows, live text, lighting, movement and LOD.
- World change, chunk unloading, rail edits/network revisions and rapid camera movement.
- Profile Spark CPU + GPU and compare FPS, 1% lows and draw calls against fixed STRATA scene.
