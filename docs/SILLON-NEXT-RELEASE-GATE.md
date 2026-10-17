# Sillon NEXT — mandatory release contract (Forge 1.20.1 / Fabric 1.20.4)

**This is the successor project to SOLIS, not a claim that the requested engine is finished.** Codename NEXT is temporary. The two SOLIS branches remain untouched. Never present a build as complete without both platform validations.

## Release-blocking features

1. `gpu_rail_culling` — implementation + regression test + real-world verification on both platforms.
2. `multi_draw_indirect` — implementation + regression test + real-world verification on both platforms.
3. `train_instancing` — implementation + regression test + real-world verification on both platforms.
4. `multi_material_atlas` — implementation + regression test + real-world verification on both platforms.
5. `gpu_scene_selection` — implementation + regression test + real-world verification on both platforms.
6. `shader_compatibility` — implementation + regression test + real-world verification on both platforms.
7. `rail_geometry_cache_invalidation` — implementation + regression test + real-world verification on both platforms.
8. `rail_lighting_and_visibility` — implementation + regression test + real-world verification on both platforms.
9. `train_motion_and_fps_regressions` — implementation + regression test + real-world verification on both platforms.
10. `panam_shader_display` — implementation + regression test + real-world verification on both platforms.
11. `panam_ui_item_brush` — implementation + regression test + real-world verification on both platforms.
12. `panam_network_line_badges` — implementation + regression test + real-world verification on both platforms.
13. `jcm_compatibility` — implementation + regression test + real-world verification on both platforms.
14. `mtr_france_compatibility` — implementation + regression test + real-world verification on both platforms.
15. `dense_station_benchmarks` — implementation + regression test + real-world verification on both platforms.
16. `forge_1201_build` — implementation + regression test + real-world verification on both platforms.
17. `fabric_1204_build` — implementation + regression test + real-world verification on both platforms.

Specific requirements:
- **GPU rail culling** must be genuinely active on supported GL4.3, conservative (no missing rails), restore indirect instance counts across shader/shadow transitions, and fail open on unknown bounds.
- **Multi-Draw Indirect** must reduce real draw calls, preserve materials and draw order; fall back to GL3.3, unsupported drivers and shader packs.
- **Train instancing** must preserve animations, doors, tint, lighting, custom JCM/MTR France models and visibility.
- **Multi-material atlas** must group heterogeneous compatible models, not claim arbitrary shader-pack/bindless cross-texture support without actually implementing it.
- **GPU scene selection** must move meaningful broad-phase selection/command ownership to the GPU; current CPU sector selection alone is insufficient.
- **Shaders**: Oculus (Forge) and Iris (Fabric) / Complementary, main and shadow passes, block entities and transparent objects must be visually verified.
- **Rails**: correct live block lighting, cache invalidation after edits/network/world reload, no always-lit rails or view-angle holes.
- **Trains**: no slowdown/motion regression and no catastrophic station FPS regression.
- **PANAM**: visible under shaders, accessible settings UI, screen item and brush controls, notices for other lines with their real badges.
- **Addons**: JCM presets/PIDS and MTR France models and screens tested with each target.
- **Benchmarks**: same dense station, same view distance, GPU, shaders, JCM/MTR France, runs with stock MTR, SOLIS and NEXT; report average FPS, 1% low, CPU/GPU timing, draw counts, screenshots/logs.
- **Artifacts**: Forge 1.20.1 and Fabric 1.20.4 built successfully, loaded in Minecraft and smoke-tested.

## Status / release process

The source already contains partial GPU implementations from SOLIS; existence of a class or a green CI build is NOT validation. Current status: **all 17 gates unverified** on both targets. See `docs/SILLON-NEXT-EVIDENCE.json` for the explicit manifest.

CI publishes only `Sillon-NEXT-EXPERIMENTAL-...` artifacts. `workflow_dispatch` with `release_candidate=true` runs `tools/check_next_release_gate.py`; it fails until every gate has documented evidence on both targets. A completed evidence manifest still requires human review.

## First concrete fix in this branch

GPU rail command culling now computes the camera forward vector using Minecraft yaw/pitch conventions instead of rotated basis-vector components. The regression test checks six cardinal/up/down camera directions and the 32-block conservative near bubble. This is a math/source test, NOT an in-game OpenGL visual test.

Use `-Dmtr.mega.railCompute=false`, `-Dmtr.mega.materialAtlas=false` and `-Dmtr.mega.gpuDriven=false` for A/B isolation of GPU regressions.
