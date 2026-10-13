# MTR Engine V4 — GLACIER (E1 + E2 fusion)

Based on V3 scene/shadow branch. This branch is EXPERIMENTAL, not a validated FPS release.

## Implemented
- Render-thread-only invalidation of persistent rail scene plans, cached rail poses and GPU pages when `MinecraftClientData.networkRevision` changes; prevents stale rail geometry after network updates.
- `ScenePlanCache.invalidateWhere` and `PersistentRegionCache.invalidateWhere`: event-targeted invalidation APIs that keep unrelated sector content resident.
- `StaticRegionRenderer.invalidateSector(x,y,z)`: drops only a changed 16-block sector's scene plans, layout and GPU meshes. Must be called on the render thread between passes.
- Per-frame absolute train-car and bogie transformation memoization, reused across main/Oculus passes where the absolute pose and oscillation amount match. Rider-relative paths remain uncached. Opt out with `-Dmtr.engine.trainTransforms=false`.
- Native Java smoke tests for scene-plan invalidation, retained GPU-page lifetime, frame-only memoization, plus source integration checks.
- CI compiles Forge 1.20.1 and, on success, publishes the shaded JAR directly to a GLACIER GitHub *prerelease*, with run-number suffix and SHA-256 sidecar.

## Important limitations / E1+E2 backlog
- No in-game FPS benchmark has been run. The existing V2 74 AVG FPS is not a V4 result.
- Network revisions conservatively invalidate all rail pages. Per-sector invalidation APIs exist but are **not yet wired to automatic changed-rail diff events**.
- Ordinary textured (classic) rail quads still go through `MainRenderer.scheduleRender`; **full persistent classic-rail GPU mesh generation is not complete**.
- Train model queueing, doors, animations, oscillations, sounds and lighting remain dynamic; only absolute transformation objects are memoized.
- Oculus compatibility, shader-order rendering and JCM beta need in-game verification.
- Prior `work/mtr-engine-100fps-cpu-v1` reportedly regressed from 74 AVG to 64 AVG; do not merge this branch until measured.

## Reference and fallback
- **Stable:** `work/mtr-engine-queue-hotpath-v2` / tag `v2.0.0-ALPINE` (published by its workflow after a green build).
- **Candidate:** `work/mtr-engine-v3-100fps-scene-shadow`.
- **Experimental:** `work/mtr-engine-v4-fusion-scene-rail-train`.

## When in-game testing is possible
Use same world (5 sidings, 2 stations), 12 chunks, Complementary HIGH, same Java heap and player/camera pose. Record three 60-second FPS/Spark profiles for V2 and V4 after two-minute warmup; compare median AVG, 1%-low and phase metrics; check moving trains, doors, shadows, JCM, world reload and resource reload.

If V4 regresses, first disable `mtr.engine.trainTransforms` to isolate train transform memoization. V2 ALPINE remains the recovery build.
