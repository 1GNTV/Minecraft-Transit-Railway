# Forge 1.20.1 rendering recovery

Experimental branch: `work/mtr-v4-optimization`, based on V4 `2b0483968a1f75facf8e63a7f7b4e6d4e2eb1c4d`.

## Baseline mistake

V4 descends from `perf/forge-1201` (`0242e7b`), which predates the render pipeline work in `perf/forge-1201-render-pipeline` (`dadf9c9`). Comparing those heads shows V4 lacks the Forge performance mixin registration, allocation-free material/attribute hashing, reusable matrix buffer, generic attribute cache, and hidden translucent queue cleanup. The older V4 architecture note incorrectly equates the earlier branch with the complete V2 rendering baseline.

This is a concrete source difference consistent with the regression; it does not prove the whole FPS difference. Preserve both original branches and JARs for controlled comparison. No V2 or V3 branch is modified by this work.

Reported FPS: V2 100–125 without shaders / 65–75 with shaders; V4 80–100 without shaders (minimum 75), 50–60 with shaders (40–50 during a train arrival). These are user observations, not newly measured benchmarks.

## Actual Spark analysis

Both supplied profiles contain 60,000 ms of render-thread samples, execution mode, Java sampler, 4,000 microsecond sampling interval. They are V4 captures under different shader conditions, **not** a paired V2/V4 benchmark. `YygmrtCgp0` has MTR's custom-shader matrix-attribute path; `gJd8iBO0Tt` contains Oculus/Iris ExtendedShader uniform updates. This source/stack evidence supports the shaderless/shader distinction, but neither binary identifies a Git commit.

| Render-thread inclusive samples | YygmrtCgp0 | gJd8iBO0Tt |
| --- | ---: | ---: |
| MainRenderer.render (recursive frames counted once) | 46.253% | 53.220% |
| BatchManager.drawAll | 27.967% | 34.827% |
| VertexAttributeState.apply | 22.013% | 29.460% |
| RenderRails.render (submission only) | 3.507% | 2.147% |
| MTR VertexArray.draw → glDrawElements | 4.427% | 3.433% |
| Iris ExtendedShader.apply (all callers) | absent | 28.220% |

The first profile spends 15.893% self samples in GL20C.glVertexAttrib4f and 5.340% in GL30C.glVertexAttribI2i (all callers). The second shifts work into Iris CustomUniforms.push, matrix uniforms and framebuffer binding. Inclusive rows overlap and must not be added.

`OptimizedRenderer.queue()` makes a copied matrix per model submission. `BatchManager` groups by material but still issues one draw per uploaded VertexArray. Each draw applies both submission and material attribute states. In shader mode, the matrix case sets MODEL_VIEW_MATRIX then calls the full shader apply, triggering Iris texture/framebuffer/custom-uniform work. Shaderless mode sends four matrix-column generic attributes. The included binary was inspected, rather than assuming current upstream sources matched it: upstream `683092fc` has newer protected-state and OBJ APIs absent from this JAR.

Spark samples native-driver calls but cannot separate CPU submission from driver/GPU stalls or supply draw counts or frame-time percentiles. No FPS gain is inferred from these percentages. Use `tools/analyze_spark.py` for a local-only reproducible decode using the [Spark sampler schema](https://github.com/lucko/spark/blob/master/spark-common/src/main/proto/spark/spark_sampler.proto).

The installed Oculus 1.8.0 ExtendedShader bytecode was also inspected. Its `apply` override recalculates model-view inverse and inverse-transpose normal matrices, uploads derived uniforms, pushes custom uniforms and binds textures/framebuffers. Updating MODEL_VIEW_MATRIX alone can leave derived state stale. This is why the work branch retains the complete Oculus apply and aims to reduce the number of model mesh draws that require it, rather than bypassing shader hooks.

## Changes

1. Restore V2's allocation-free hash calculations and reusable 64-byte matrix staging buffer without changing attribute values or shader behavior.
2. Restore generic-attribute deduplication **only for the shaderless/custom MTR shader path**, with independent kill switch. Scope it to a protected pass and invalidate before/after each material shader setup/cleanup. After each draw, invalidate locations sourced from enabled vertex/instance arrays: their current values can become undefined per OpenGL. Nested and failed passes disable/clear the cache. Keep every VAO bind and every full ShaderInstance/Oculus apply. See [OpenGL generic attribute semantics](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glVertexAttrib.xhtml).
3. Clear undrawn translucent queues so hide-translucent mode cannot accumulate old geometry/train transforms.
4. Combine equal **solid** OBJ material meshes at model upload, before GPU submission. This uses existing RawMesh append/upload semantics, preserving source-local normal generation and face index offsets. Translucent, glowing cutout and non-depth-writing meshes remain separate and ordered. No merge across separate cars, animation/door conditions, model submissions, or light values. One-part uploads keep the original path. This reduces actual draws only when multiple parts of a single OBJ submission share solid materials; it cannot help already-consolidated single-material Blockbench rails. No instancing, shader patching or per-frame GPU mesh upload is introduced.
5. Opt-in per-pass wall durations on the render thread and exact MTR draw/material counters, separated by shaderless/main/shadow paths, plus OBJ source/uploaded mesh counts. The timer includes native-driver waits and is not CPU-only execution time, a GPU timer or FPS.
6. Register these mixins only on Forge 1.20.1 and verify classes/config registration in the final shaded JAR. The V3 uniform-only shader bypass and VAO-bind suppression are excluded.

## A/B switches

Restart after changing JVM options; reload already-created models after choosing merge behavior.

| Option | Default | Purpose |
| --- | --- | --- |
| `-Dmtr.v4.attributeCache=false` | true | Disable shaderless generic attribute deduplication |
| `-Dmtr.v4.mergeOpaqueObj=false` | true | Keep individual OBJ uploads |
| `-Dmtr.v4.metrics=true` | false | Log render-thread pass duration, MTR draws/materials and OBJ consolidation |
| `-Dmtr.v4.railIndex=false` | true | Original pre-V4 global rail selection |
| `-Dmtr.v4.frustumBasis=false` | true | Original per-segment camera rotations |
| `-Dmtr.v4.pidsCache=false` | true | Original auto-PIDS lookup |

For a V4 Java-path isolation run, disable the final three options. Test attribute cache and OBJ merging separately before testing both. Original V2 remains the comparison artifact; the work JAR with switches is not an exact V2 binary.

## Acceptance and limitations

CI exercises actual merged mesh geometry (material boundaries, face offsets, unchanged source topology, separate uploads) and the actual cache helper with a recording GL stub (external renderers, material/array boundaries, scalar types, signed zero, shader fallback, nested/failing passes). The final shaded JAR must include and register all mixins.

Compilation and these tests are not a live Mixin-application test or an Oculus/MTR France Addon rendering validation. Keep the full shader application and public MTR/mapping APIs, but verify in game: fixed station camera and identical settings, idle/train arrival/train interior, shaders on/off, doors, bogies, PIDS, transparency/glow, F3+T, resource packs, rail edits and world/dimension changes. Capture 60-second FPS median/1% low/frame-time traces and Spark plus the logged draw counts. Warm up the same world before each run and record exact JAR hash/options. No in-game FPS improvement is claimed.

Read-only inspection of the locally installed MTR France Addon `2.0.0+1.20.1` found its mixins target rail schemas/types, init accessors, creative inventory and GraphicsHolder access. It does not bundle the BatchManager/OptimizedModel classes targeted here or list a mixin targeting them. This avoids a direct known target collision; it is a static compatibility check, not a rendering test.

## Build evidence

First build passed: commit `40a46abdfedacfc9036b8f18faece18daa3215ea`, [run 38092417343](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38092417343). Forge compilation, geometry tests, cache boundary tests and shaded-JAR registration/integrity checks succeeded. Shaded JAR: 84,303,835 bytes. Artifact archive SHA-256: `76f907ec8b4a43e8f944a5637447f15217a096de71915f00c8c4601bba900623` (ZIP hash, not JAR hash).

Final build passed: code commit `b0c19aae7b36b0c5912ce6ce37947edc24c22270`, [run 38093048769](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38093048769). Three geometry tests passed with zero failures/ignored tests. The cache lifecycle and kill-switch tests passed. Final shaded bytecode method/field/invocation checks, eight-mixin registration and ZIP integrity checks passed. The complete job log was inspected: no compiler errors; two inherited Javadoc `@author` placement warnings remain non-blocking.

[Final Forge 1.20.1 artifact](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38093048769/artifacts/11684754231): 84,303,838-byte JAR, SHA-256 `6f00080c74353a45ccac77416ed373b472073a9b66ff29dbe4872ca9a8d7bda9`. Archive SHA-256 `fa4791d2517ec47a1d1c7646e40b57aca4987a9b6270ac5cabdf69268eb0ed90`. Both were downloaded and verified locally. The extracted local artifact is named `MTR-forge-4.0.5+1.20.1-work-v4.jar`; renaming does not change its bytes.

The final code build includes the cache kill-switch test, static field/method/invocation checks against the shipped mapping bytecode and explicit JAR SHA-256 output. It clarifies that pass duration is wall time on the render thread. Subsequent documentation-only commits do not change the compiled code. PR remains draft until in-game shader/addon and FPS A/B acceptance; the existing installed game profile is untouched.
