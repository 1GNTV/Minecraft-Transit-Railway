# SILLON STRATA — Stable

**Forge 1.20.1 · Minecraft Transit Railway 4 fork · single SILLON mod JAR (`mtr`)**

This stable release freezes the **exact tested STRATA b165-r1 binary**, with no additional renderer changes. It is the preferred reference build for the STRATA generation, based on GLACIER rather than the experimental AURORA branch.

### What's included

- The STRATA rail-selection and persistent-region rendering engine, retaining GLACIER's verified performance work.
- The b165 vehicle-position cache, which avoids repeatedly recomputing complete train/bogie trajectories in calls made by JCM / MTR Scripting.
- Separate cache states for normal and smoothed positions, revision/route invalidation and isolated mutable return values.
- Original MTR mod ID `mtr`, interfaces, and JCM Mixin entry points. **JCM remains a separate, unchanged addon**, not bundled in SILLON.
- Forge build, shade check, exact JCM 2.3.0-beta.1 callback descriptors and 30 addon member-contract checks passed in CI.

### Performance observations

On one laptop, **STRATA b165 with JCM** was observed around **106–110 average FPS**, versus approximately **111 average FPS for GLACIER without JCM** in a later same-laptop comparison. A separate cache ON/OFF test reported **120 vs 101 average FPS**, but 120 FPS was not the sustained STRATA baseline. Results depend on scene, addons, shaders and measurement conditions; they are not universal benchmarks or guaranteed gains.

### Known issue

A **brief intermittent visual disturbance of some trains while decelerating toward a full stop** has been reported. It has not yet been isolated or fixed and may involve interpolation or render-pass interactions. "Stable" designates the current preferred STRATA baseline, not a claim that all visual defects or addon compatibility cases are resolved.

### Install and rollback

Replace the existing MTR/GLACIER/AURORA/STRATA JAR in `mods` with **only** the SILLON STRATA Stable JAR. Do **not** install two implementations of `mtr` together. Keep JCM (and other required addons) separately installed.

To disable only the new b165 cache for diagnosis, add JVM argument `-Dmtr.strata.vehiclePositionCache=false`. This retains all other STRATA features but may substantially reduce FPS with JCM.

**Build source:** [STRATA b165-r1](https://github.com/1GNTV/Minecraft-Transit-Railway/commit/78153fdc00fd02fdc5419fd455fcc8b4239cb3dc) · [successful CI run](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38472205744)

Development of future versions is not included in this release.
