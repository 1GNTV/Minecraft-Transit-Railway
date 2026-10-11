# MTR Forge 1.20.1 optimization work

- 2026-10-11: Created `work/mtr-v4-optimization` remotely and locally from `perf/forge-1201-v4` / `2b048396`.
- Profiles reopened from prior conversation attachments, decoded locally as Spark protobuf. Both are 60-second V4 render-thread captures, not V2 baselines.
- Found V4 based on older `perf/forge-1201` / `0242e7b`, missing V2 render-pipeline improvements at `dadf9c9`.
- Exact Forge mapping dependency inspected with javap; current upstream source differs, so mixin targets were checked against shipped bytecode.
- Route: Forge 1.20.1 client-only mixins on MTR mapping classes, allocation restoration, safer shaderless attribute caching and equal-solid-material OBJ upload consolidation. Keep complete shader apply, VAO binds, queues, train/door transforms.
- No game profile/save/JAR changes performed. Original V2/V4 branches remain unchanged.
- Detailed source/profile findings, switches and verification limits: `docs/mtr-v4-work-optimization.md`.
- First CI passed: `40a46abdfedacfc9036b8f18faece18daa3215ea`, run `38092417343`. Forge compilation, geometry tests and JAR verification succeeded. Artifact `11684159118` retrieved through the connector; no compile errors. Two inherited Javadoc warnings about method-level `@author` remain non-blocking.
- Follow-up: cache kill-switch test, exact bytecode target checks and JAR SHA-256 output; clarify render-thread wall duration. In-game shader/addon A/B acceptance remains pending.
- Final CI passed: code `b0c19aae7b36b0c5912ce6ce37947edc24c22270`, run `38093048769`. Three geometry tests, cache lifecycle/kill-switch, eight-mixin registration, shipped bytecode targets and JAR integrity passed; full logs inspected, no compiler errors.
- Final artifact `11684754231` downloaded and extracted locally: 84,303,838 bytes; JAR SHA-256 `6f00080c74353a45ccac77416ed373b472073a9b66ff29dbe4872ca9a8d7bda9`. Local archive/JAR hashes match GitHub and CI. Final test report retrieved: 3 tests, 0 failures, 0 ignored.
- Draft PR: https://github.com/1GNTV/Minecraft-Transit-Railway/pull/1. FPS gains and live Oculus/France Addon compatibility remain unmeasured; no game installation changes made.
- 2026-10-11: User authorized implementation of the full performance map. Started phased work; scene is the central interchange station in an old shared survival with two metro lines. Added mixed OBJ/Blockbench upload consolidation, scoped per-family counters and optional nonblocking GPU timestamps; local diagnostic/cache tests passed. See docs/work-progress.md for unfinished scope and validation state. RawMesh.append shares vertices (copies face indices); future geometry baking must explicitly copy vertices.
### Work : prototype de secteurs de rails

Regroupement shaderless optionnel des rails opaques statiques : sélection originale conservée, lumière par segment, signature exacte, construction et résidence bornées. Ressources incompatibles et shader packs conservent le chemin précédent. Aucun gain FPS annoncé ; tests de propriété/invalidation locaux réussis, géométrie et compilation en CI.
### Work : images dynamiques et propriété des buffers

Résultats de textures invalidés par génération, file de complétions et publications bornées, recadrage CPU sur worker avec libération des images remplacées. Propriété explicite des buffers créés par le compilateur Work, fermeture unique des aliases et nettoyage des buffers à la collecte. Tests locaux de capacité, races et libération réussis. Correction du nom de matériau glow dans le test CI du prototype de rails.

### Next : premiers composants d'architecture

Instanciation shaderless GL 3.3 par séries compatibles, trois pages bornées et fences non bloquantes ; VAO sources inchangés. Cache de poses par vraie image Forge/révision de véhicule. Réordonnancement des triangles opaques et sommets à l'upload. Les six groupes de tests locaux passent. Cible du redirect vérifiée dans le JAR : `BatchManager.lambda$drawBatch$2`, et non `drawBatch` dont le corps appelle une lambda. Les autres grands chantiers restent suivis explicitement dans `docs/mtr-next-version-fr.md`.

### Next — compilation de shaders et couverture des modèles

Suppression des recompilations des trois shaders internes lors de chaque upload, avec invalidation par génération de ressources et contrôle du succès complet. Aucun changement à ShaderInstance.apply / setupShaderBatchState / uniformes Oculus. Tests de succès, échec partiel, gestionnaire distinct, invalidation et option de désactivation. Réordonnancement opaque étendu aux chemins originaux Blockbench et OBJ isolé ; propriété des buffers attachée aux VAO. Suppression des allocations de tâches d’occlusion non utilisées pendant les ombres. Recherche des candidats triangles par présence en cache en O(1), pour limiter le coût de préparation. Cibles synthétiques contrôlées dans la dépendance distribuée ; validation Mixin en jeu encore requise.

Next final vérifié : code 8574d7fb788b17e4dd19f0189948475ee260135d, CI 38146219733 réussie, six groupes de checks Python et onze tests de géométrie. JAR SHA256 746ea60c7a66df2b5ad6698121035413185e5b59c8a25cf460ca5f5c8e2d1b65. Rapports et artefact récupérés ; documentation des limites et options actualisée. Aucun benchmark en jeu.
