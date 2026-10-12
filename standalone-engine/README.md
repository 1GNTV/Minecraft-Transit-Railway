# MTR Engine standalone (Forge 1.20.1) — proof of integration

This folder is a completely **separate Gradle/Forge mod** inside the
development branch `work/mtr-engine-standalone-addon-forge1201`.
It creates `mtrengine`, an independent mod requiring the **official** MTR
4.0.5, similar in approach to Joban Client Mod.

## What really works in this prototype

- A second Forge JAR with its own `modId=mtrengine`; official MTR remains
  `modId=mtr` and is **not shipped in the addon**.
- Gradle compilation against an external published MTR dependency.
- Two mixins anchored on the real MTR 4.0.5
  `MainRenderer.render(GraphicsHolder,Vector3d)` and
  `RenderRails.render()` entry/exit points.
- Non-destructive pass-boundary bridge, allowing an external renderer to
  know when a MTR scene / rail scene starts and finishes.
- Optional CPU wall-time hook diagnostics via
  `-Dmtr.engine.addon.diagnostics=true`.

## Critical: not yet a performance release

**This addon does not yet contain the persistent region batching implementation,
and it does NOT claim the 79–80 FPS of the integrated fork.**
It deliberately leaves all official MTR drawing untouched.
It proves the package/dependency/mixin integration first.
The next stage is to port `StaticRegionRenderer`, `RailSectorBatcher`,
`PersistentRegionCache`, `RegionVertexRanges`, and GL resource lifecycle,
then replace the MTR 3D rail callsite with a guarded, versioned operation
wrapper. That wrapper must always be able to call the original rendering on
incompatible shaders, model formats or addons.

MTR and Joban Client Mod both modify rail rendering through mixins.
All adapters must be tested for **mixin conflicts** with JCM, MTR France Addon,
Embeddium and Oculus. Avoid `@ModifyArgs` on Oculus: this produced
`NoClassDefFoundError: org/spongepowered/asm/synthetic/args/Args$1`
in the earlier fork.

## Build

From the root of this fork:

```sh
./gradlew -p standalone-engine clean build
```

Then inspect `standalone-engine/build/libs/`. Install just the addon JAR
alongside the **official MTR**, never alongside the customized MTR fork:
both official MTR and the fork claim `modId=mtr` and conflict.

## Upgrade strategy

- Keep this subproject completely separate from the original MTR sources.
- Compile against pinned upstream artifact(s), not our fork.
- Version adapters against exact MTR versions and guard changed signatures.
- Run headless compile+structural tests automatically after dependency bumps.
- Gate release on actual Minecraft startup, rail editing, loading, shadows,
  addon compatibility, and an identical 60-second A/B benchmark.
- Do **not** auto-release after a passing Gradle compile: shader packs
  and runtime mixin conflicts need a real client test.

Current first supported target: Forge 1.20.1, upstream MTR 4.0.5.
