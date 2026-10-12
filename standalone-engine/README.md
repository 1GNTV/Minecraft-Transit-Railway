# MTR Engine standalone — Queue Hotpath V2 (79 FPS) port

This **independent Forge 1.20.1 addon** is based explicitly on the earlier,
user-benchmarked `work/mtr-engine-queue-hotpath-v2` reference (~79 FPS AVG
with Complementary HIGH). It deliberately does **not** contain the subsequent
`work/mtr-engine-100fps-cpu-v1` experiments; those had inconsistent FPS
during the user's runtime testing.

## Target package

Install **official MTR 4.0.5** and **MTR Engine standalone** as two
separate mods. MTR remains `modId=mtr`; our addon is `modId=mtrengine`.
Never install the integrated MTR fork in the same mods folder.

Standalone code includes the tested scene/region caches, rail poses, page
layouts, fast region mesh uploads, GL multi-draw selections, OBJ/Blockbench
source geometry compilation, conservative spatial rail index, and Queue
Hotpath V2 rail traversal logic. These modules use distinct
`dev.igntv.mtrengine` packages to avoid collisions with official MTR.

Stock MTR's rendering stays active by default.

## Experimental enablement

On an expendable test installation, add to JVM arguments:

```
-Dmtr.engine.addon.regions=true
```

The addon hooks the concrete RailResource and OptimizedModelWrapper model
factories to retain opaque geometry, then substitutes its optimized rail
traversal and flushes sector pages at the upstream MTR render boundaries.
If the snapshot or rendering prerequisites are missing, the rail still
uses upstream `RailResource.render` as its fallback. Non-rail models are
left alone by the model factory mixin.

Do not also set `-Dmtr.next.regions=false`: it would disable precisely the
GPU region feature to be tested.

Optional diagnostics (should be OFF during FPS benchmark):

```
-Dmtr.engine.addon.diagnostics=true
-Dmtr.engine.metrics=true
```

## What is and isn't verified

- The extracted pure-Java region caches have standalone behavioral tests.
- The Forge build verifies standalone mod identity, MTR dependency, Mixin
  configuration and shader-independent Java compilation.
- Build success does NOT prove the mixins applied successfully at runtime.
- **No equivalent 79 FPS benchmark has yet been measured with this addon.**
- Real-world testing with Embeddium, Oculus, Complementary HIGH, France Addon,
  shadows, animated vehicles, resource reload, world switches and editing is
  still required before any public release.
- The 79-FPS fork's broader train, texture, material and Oculus optimizations
  are not all externalized yet. This branch targets the central *rail region*
  engine first. No unsupported `@ModifyArgs` shader hook is enabled.
- Some upstream changes or other addons may conflict with injected calls;
  the official MTR version is bounded to `[4.0.5,4.1)`.

## Maintenance direction

For upgrades, pin exact upstream MTR builds, run regression compilation,
review changes to MTR's rendering call sites and GPU mapping internals,
and smoke-test the client plus shader packs. Prefer independent adapters
and original-call fallbacks over overwriting MTR classes or bundling MTR.

Original MIT license and copyright notices must be preserved when
redistributing portions of the upstream code.
