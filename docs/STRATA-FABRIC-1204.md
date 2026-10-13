# SILLON STRATA — Fabric 1.20.4 (EXPERIMENTAL)

This port is based on the **unmodified STRATA Stable b165 source** (`78153fdc`), not on HELIX. It keeps the `mtr` mod ID and ships in one JAR.

**JCM target:** [JCM 2.2.5 Fabric 1.20.4](https://github.com/DistrictOfJoban/Joban-Client-Mod/releases/tag/v2.2.5), paired with MTR 4.0.5.

The existing STRATA CPU rail-visibility, retained rail-plan, vehicle position, and GPU region renderer source is shared with Forge. Fabric now registers the MTR mapping-layer performance mixins. It does not bundle JCM or alter JCM binaries. CI compiles against Minecraft Fabric 1.20.4, remaps the complete mod JAR, checks Mixin declarations and the JCM synthetic lambda callback descriptors, and verifies the single-mod artifact.

**Important:** Passing CI does not prove JCM, Sodium, Iris, scripts, rail editor or world loading will all work. This is an **experimental compatibility build**, not STRATA Stable Fabric. Do not overwrite your world without backups. Keep only ONE implementation of the `mtr` mod, plus Fabric API and the matching JCM Fabric 1.20.4 JAR. Forge JARs must NOT be used with Fabric.

Please test map opening, long rails, train braking, JCM PIDS, train scripting, shaders on/off, shadows, GUI, and server connectivity. Capture `latest.log`, FPS for the same scene, and any crash report; no performance improvement versus Forge 1.20.1 is claimed.

**Fallback:** STRATA Stable Forge 1.20.1 is untouched.
