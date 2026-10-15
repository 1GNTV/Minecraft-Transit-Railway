# SILLON STRATA — Fabric 1.20.4 (EXPERIMENTAL)

This port is based on the **unmodified STRATA Stable b165 source** (`78153fdc`), not on HELIX. It keeps the `mtr` mod ID and ships in one JAR.

**JCM target:** [JCM 2.2.5 Fabric 1.20.4](https://github.com/DistrictOfJoban/Joban-Client-Mod/releases/tag/v2.2.5), paired with MTR 4.0.5.

The existing STRATA CPU rail-visibility, retained rail-plan, vehicle position, and GPU region renderer source is shared with Forge. Fabric now registers the MTR mapping-layer performance mixins. It does not bundle JCM or alter JCM binaries. CI compiles against Minecraft Fabric 1.20.4, remaps the complete mod JAR, checks Mixin declarations and the JCM synthetic lambda callback descriptors, and verifies the single-mod artifact.

**Important:** Passing CI does not prove JCM, Sodium, Iris, scripts, rail editor or world loading will all work. This is an **experimental compatibility build**, not STRATA Stable Fabric. Do not overwrite your world without backups. Keep only ONE implementation of the `mtr` mod, plus Fabric API and the matching JCM Fabric 1.20.4 JAR. Forge JARs must NOT be used with Fabric.

Please test map opening, long rails, train braking, JCM PIDS, train scripting, shaders on/off, shadows, GUI, and server connectivity. Capture `latest.log`, FPS for the same scene, and any crash report; no performance improvement versus Forge 1.20.1 is claimed.

**Fallback:** STRATA Stable Forge 1.20.1 is untouched.

## PANAM PIXEL integration

This experimental compatibility branch starts from PANAM PIXEL b179, includes HELIX and STRATA, and uses the Fabric 1.20.4 build and 2.2.5 JCM ABI adaptations. In-game compatibility remains unverified.


## PANAM control centre (Fabric 1.20.4 preview)

Use `/mtr panam` as a server operator to manage multiple global or per-line announcements. Use the arrows to change line, announcement and type; select **Ajouter**, **Enregistrer** or **Supprimer**. The network synchronizes the notices to already placed PIXEL screens, retaining existing local brush messages. Each screen displays up to eight notices, cycling every ten seconds. Types are Information, Travaux, Danger and Arrêt complet. Stop notices activate the special suspended-service layout.

**Parisine licensing:** this experimental JAR deliberately does not redistribute the font files from the PANAM reference repository, whose reuse licence could not be verified. To enable actual Parisine on your own installation, put lawfully obtained `Parisine-Regular.otf` and `Parisine-Bold.otf` in `~/.sillon/fonts/` (e.g. `C:\\Users\\<your-user>\\.sillon\\fonts\\`), then restart Minecraft. PIXEL loads those files first, then checks a local Minecraft resource pack at `assets/mtr/font/panam/parisine-regular.otf` and `parisine-bold.otf`, then falls back to Noto Sans. Without locally supplied Parisine the JAR will still use its fallback.

<!-- CI trigger: rebuild PANAM hanging Fabric 1.20.4 without modifying runtime behavior. -->
