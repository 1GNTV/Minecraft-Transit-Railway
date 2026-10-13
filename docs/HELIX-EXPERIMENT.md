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

The more invasive dense-ID rail-engine rewrite, universal reusable rail
submission packets, full separation of simulation/interaction/audio from
rendering, generic cross-addon ABI/runtime checks, full rail-light shader
parity test and fixed visual screenshots. These require additional isolated
implementations and runtime evidence; this first candidate must not be
presented as completing all roadmap priorities.

## How to test

Run the same map and camera with Complementary + Oculus + Embeddium.
Compare STRATA Stable and HELIX in a fixed 60-second benchmark, with JCM
unchanged. Check normal/late braking, multiple shadow cascades, stopped
doors/PSD, animated models, classical and custom rails, bright sunlight and
dark tunnels. Capture `latest.log` and both shader-on and shader-off
screenshots. Open the map and rail editor. If the FPS falls, try the two
flags individually to isolate the change. No FPS gain is claimed before
matched user benchmarks.
