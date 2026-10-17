# Sillon + JCM v2 + MTR France — Fabric 1.21.1

This is an **experimental source port**, not a working modpack or a release.

## Goal

Build three compatible Fabric 1.21.1 mods: Sillon SOLIS (MTR replacement), Joban Client Mod v2 and MTR France Addon.

## Source baseline

- Sillon: copied from `work/sillon-solis-fabric-1204` at `0e6a4795459074cfafeb6fe9de21edb5c21be6aa`.
- JCM: `DistrictOfJoban/Joban-Client-Mod` commit `fc502c4b45b581a4407aefb890c18691a3f34ea6` (MIT).
- MTR France: `MTR-France-Team/MTR-France-Addon` commit `eaf2a8130ac31f12534480a567c9246f35c0463e` (MIT).
- Initial 1.21.1 dependency target: MTR 4.1.0-beta.2.

## CI

The workflow `.github/workflows/sillon-trio-1211.yml` attempts independent source builds and uploads error logs. No build should be considered working unless compilation succeeds, metadata targets Fabric 1.21.1, the three mods start in one instance, and in-game tests pass.

The current Sillon 4.0.5 source does **not** have `Minecraft-Mappings-fabric-1.21.1` in `libs/`. This is a known blocker, not an overlooked build configuration switch. Source and mapping API adaptation are necessary. Addon dependency upgrades are only first-pass scaffolding; source-level API errors are expected.

## Validation before delivery

1. Java 21 and Minecraft 1.21.1.
2. Fabric Loader + Fabric API; only one MTR implementation installed (Sillon).
3. Sillon startup, rails/trains, shaders and PANAM.
4. JCM presets/PIDS, block entities and screens.
5. MTR France rolling stock, signs, blocks and renderers.
6. Joint client and dedicated-server startup, save/reload, and regression performance tests.

Do not install diagnostic or failed builds into an existing world. Back up the world first. Keep Forge 1.20.1 and Fabric 1.20.4 releases unchanged.

## First GitHub Actions diagnostic run (39045511960)

- Sillon: failed in old Fabric Loom Minecraft merge, ASM cannot read Java 21 class version 65. Upgrade Loom and Gradle first. The 1.21.1 mapping library is still missing.
- JCM: MTR 4.1 binary was built with Loom 1.16.3, while JCM used Loom 1.10.5. Upgrade Loom and Gradle.
- MTR France: UniversalCraft does not publish a `1.21.1-fabric` module. Try its `1.21-fabric` artifact for 1.21.1 and then verify binary compatibility.

## Second GitHub Actions diagnostic run (39045824090)

- Sillon: Loom/Gradle upgrade passed plugin setup; Gradle 9 removed `shadowJar.archivePath`. Fixed task input property in next attempt.
- JCM: Loom upgrade resolved MTR 4.1 remap dependency, but ForgeGradle 6 refuses Gradle 9 even when building only Fabric. Exclude Forge subproject in next attempt.
- MTR France: `1.21-fabric` UniversalCraft resolved, compilation proceeded, then >100 source errors, notably missing `org.mtr.mapping.*` classes under MTR 4.1. This requires actual API migration; no fake JAR should be delivered.
