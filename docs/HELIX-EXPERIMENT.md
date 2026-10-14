# SILLON HELIX — first experimental test build

HELIX is a direct experimental child of **SILLON STRATA Stable b165**,
not a replacement for the stable version. This is still ONE MTR replacement
JAR (mod ID `mtr`); Joban Client Mod, MTR France, TJMetro and MTR Scripting
remain separate unmodified addons.

## Implemented in this first candidate

- Train braking: interpolate network correction **once per main client simulation**.
  `getSmoothedVehicleCarsAndPositions()` uses a read-only sampled position
  in HELIX; shadow cascade renders cannot themselves drain smoothing debt.
  The time-based decay avoids the previous almost-frozen low-speed case
  followed by an abrupt `speed == 0` adjustment. Fallback:
  `-Dmtr.helix.stableSmoothing=false`.
- Shaders/rail brightness: the classic rail GPU path had a misleading comment
  saying disabled by default but was in fact enabled. HELIX defaults this
  *experimental* GPU rail path OFF. Original lit IDrawing fallback remains.
  Re-enable via `-Dmtr.engine.classicGpu=true`. **Actual overbrightness
  resolution is NOT yet proven**: region-model lighting or Oculus material
  paths can independently cause it. Compare both paths using night/day
  screenshots before declaring this fixed.
- CPU: reuse bounded per-sector ordered submission maps (including repeat
  same-sector lookup fast path) across shadow and main passes without changing
  location equality, duplicate semantics, fallback, or world invalidation.
  Fallback: `-Dmtr.helix.stagedGroups=false`.
- CPU: each resident GPU page now owns a reusable callback that applies its
  current model, pass-specific LOD and camera offset at invocation time.
  No stale camera position, no per-pass closure allocation.
- Existing GLACIER/STRATA region caches, JCM position cache, shader rendering,
  shadow support and MTR hooks stay in place.
- CI adds explicit HELIX motion and rendering invariants, then runs the
  original tests, Forge shaded build, exact JCM 2.3.0-beta.1 lambda ABI
  patch+strict descriptor validation and single-JAR checks on HELIX as well.

## Not yet done

This HELIX Forge 1.20.1 candidate now implements the four profiled opportunities
behind measured/fallback-safe paths:

1. **Dense rail-sector submissions** — stable numeric IDs assigned only on
   initial scene queue misses. Hot replay tickets carry the ID directly,
   removing expensive user-defined Location hashing/equality from each
   frame, with ordered fastutil sector maps preserving legacy duplicate
   membership semantics. `-Dmtr.helix.denseRails=false` disables fast
   token replay (safe full-queue path).
2. **Shadow/main rail query sharing** — reuse an exact camera-position,
   render-distance, world-object and `networkRevision` keyed snapshot of
   rail candidates across shader cascades and main pass. Real pass-specific
   culling and lighting still run for each pass, so an off-camera shadow
   caster is not incorrectly discarded.
   `-Dmtr.helix.passCandidates=false` restores independent index queries.
3. **Packed JCM vehicle snapshots** — retain per-bogie positions as
   immutable primitive doubles instead of building a second graph of
   `Vector` and pair objects for cache storage. Every addon API read
   still receives a NEW independent tree of mutable positions; signatures,
   caching revision and network invalidations are unchanged.
   `-Dmtr.helix.packedPositions=false` restores old deep-copy snapshots.
4. **GPU draw compaction** — merge only adjacent ordered index-buffer ranges
   of the same retained page. This reduces multi-draw/indirect command
   count without reordering materials or transparency. Experimental
   shader-shadow instancing and indirect draw paths exist but remain
   **disabled by default** until visually validated with Oculus and
   Complementary: `-Dmtr.helix.shadowInstances=true` and
   `-Dmtr.helix.shadowIndirect=true`.
   Disable safe range coalescing with `-Dmtr.helix.coalesceSpans=false`.

The packed positions and dense IDs are not a claim that JCM or shader
behavior has been proven at runtime. The full separation of
simulation/interaction/audio from rendering, a unified scene renderer, all
cross-addon integration tests, and complete rail-light shader parity
still require future incremental changes. No measured FPS result is
claimed for HELIX.

## How to test

Run the same map and camera with Complementary + Oculus + Embeddium.
Compare STRATA Stable and HELIX in a fixed 60-second benchmark, with JCM
unchanged. Check normal/late braking, multiple shadow cascades, stopped
doors/PSD, animated models, classical and custom rails, bright sunlight and
dark tunnels. Capture `latest.log` and both shader-on and shader-off
screenshots. Open the map and rail editor. If the FPS falls, try the two
flags individually to isolate the change. No FPS gain is claimed before
matched user benchmarks.

## Benchmark protocol (Forge 1.20.1 only)

Compare STRATA Stable and HELIX under **identical** Embeddium 0.3.31,
Oculus 1.8.0, JCM 2.3.0-beta.1, resource packs, camera and train state.
Record 60 seconds of FPS and 1% lows plus spark CPU sampling, with and
without shaders. Optional diagnostics:
`-Dmtr.engine.phaseMetrics=true -Dmtr.engine.metrics=true -Dmtr.v4.gpuMetrics=true`.

Regression checks: open map, rail editor, JCM/PIDS, moving trains and bogies,
braking/door opening, dense station models, shadows near and beyond view
frustum, classic rail lighting in daylight and tunnels, resource-pack
reload, world swap. Disable one HELIX flag at a time to isolate artifacts.
This release is a **pre-release test JAR**, not STRATA Stable.
