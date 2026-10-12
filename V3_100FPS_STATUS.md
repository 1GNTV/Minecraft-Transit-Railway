# MTR Engine V3 — benchmark candidate

Source branch: work/mtr-engine-queue-hotpath-v2, validated at 74 AVG FPS on Ryzen 7 260 + RTX 5060 Laptop, 12 chunks, Complementary HIGH.
CPU V1 regression: 64 AVG FPS. Do not merge work/mtr-engine-100fps-cpu-v1 until isolated tests prove a gain.

## Implemented in this V3 candidate

- Exact-camera, exact-render-distance rail visibility sample cache across shadow/main passes and stable-camera frames. Invalidated on rail-network sync and view change; feature toggle `-Dmtr.engine.visibleRails=false`.
- Retains the existing per-pass light, shaders, dynamic callbacks, and rail editor overlays. Only cached **immutable visible sample coordinates**, not rendered colors or GPU commands.
- One-model hot lookup for rail geometry snapshots with explicit resource-reload invalidation.
- Skips backwards player-space transforms and passenger-floor lists in shadow passes where riding interaction is impossible, while preserving gangway box computation and train geometry.
- CI Java regression checks of view stability, camera movement, rotation, render-distance change, sample-key change, and network invalidation.
- Existing phase and GPU metrics remain available via `-Dmtr.engine.phaseMetrics=true` and `-Dmtr.v4.gpuMetrics=true`.

## Not claimed as completed

- 100 FPS achieved: impossible to validate without in-game benchmark on the target world.
- Full persistent classic-rail GPU mesh generation: existing `MainRenderer.scheduleRender` callbacks remain for vanilla-shaped rails. V3 caches filtering, not textured face GPU uploads.
- Oculus shader instancing: Forge deliberately disables unsafe source rewriting `OculusInstanceShaderMixin`; the new JAR keeps its safe fallback.
- Shader-pack LOD: remains opt-in `-Dmtr.next.shaderLod=true` until screenshots and shadow regression tests; defaults unchanged.
- Complete JCM compatibility: JCM 2.3 beta's `RenderVehiclesMixin` targets an incompatible signature and needs a separate integration pass.

## Validation protocol

Test V2 versus V3 on the *same* Forge 1.20.1 instance, world, five sidings and two stations, exact camera coordinates, 12 chunks, Complementary HIGH and 6GB max Java heap. Warm up 2 minutes, then use at least three 60-second Spark/FPS recordings per build with no background games. Compare median AVG FPS and 1%-low frametimes. Check moving trains, sounds, doors, shadows, vanilla and 3D rails, and reload/world transitions.

Keep the V2 JAR archived. Any regression => toggle off `mtr.engine.visibleRails` first, or revert the V3 build.
