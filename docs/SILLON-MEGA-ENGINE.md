# SILLON MEGA — source-only integration (Fabric 1.20.4 and Forge 1.20.1)

**No compilation, CI workflow or JAR has been initiated.** The Java runtime renderer lives in `fabric/src/main/java/org/mtr/mod/render`; Forge copies that source directory in its Gradle setup. Both branches preserve distinct PANAM changes.

### 1. GPU static scene — `-Dmtr.mega.scene=false`
Existing persistent region GPU pages now remember the last activated immutable index-range mask and skip redundant activation in stable main/shadow passes. Default budgets increase from 256/1,000,000 to 384/1,500,000 resident pages/vertices, with at most two page builds per frame. This improves the existing scene engine; it is **not** a fully GPU-driven renderer.

### 2. Train/Oculus instancing — `-Dmtr.mega.instances=false`
Oculus instancing shader array capacity increases 8 -> 12 (configurable 8..16), subject to GLSL compilation and old renderer fallback. Opaque compatible groups may be conservatively re-ordered in a 32-draw screen-disjoint window when shader bridging is ready. Minimum group size 3 instead of 4. Unknown bounds, transparency, unsupported shaders retain old rendering.

### 3. Camera-independent rails — `-Dmtr.mega.rails=false`
Cache full track geometry, prepared poses and GPU replay tokens per rail/style/direction rather than keying on visible samples from the exact camera. Render pass still does camera filtering and live block lighting. World switch, network revision, reload discard cached plans. Max 512 entries / 65,536 segments / 4,096 per track; overflow, editor previews and unsupported variants take original fallback.

### JVM rollback and tuning
```
-Dmtr.mega=false
-Dmtr.mega.scene=false
-Dmtr.mega.instances=false
-Dmtr.mega.rails=false
-Dmtr.mega.oculusCapacity=8
-Dmtr.mega.instanceMin=4
-Dmtr.mega.shaderReorder=false
-Dmtr.mega.instanceWindow=24
-Dmtr.mega.sceneBuilds=1
```
Global master off resets the three upgrades, not older STRATA/HELIX features. All three default to on.

### Validation gate — NOT YET PERFORMED
Compile each branch; verify JCM/TJMetro/MTR France mixin signatures and gameplay; main/shadow rendering, 3D rails, night lighting, brush previews, moving trains, boarding, doors, PIDS/PANAM, shader pack render passes and swaps/reloads. Measure FPS mean/1% lows, draw-call counts, memory and GPU timings in the same dense scene before any performance claims.
