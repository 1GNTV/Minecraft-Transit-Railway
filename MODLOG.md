# MTR Forge 1.20.1 optimization work

- Profile-driven follow-up: use Oculus's live shadow state instead of repeated stack reconstruction, skip LOD checks without candidate geometry, and expire loaded resources by weak ordered deadlines instead of global registry scans. Scope and remaining opportunities: docs/mtr-next-profile-followup-fr.md. No FPS claim for this candidate.

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

Regroupement shaderless optionnel des rails opaques statiques : sÃ©lection originale conservÃ©e, lumiÃ¨re par segment, signature exacte, construction et rÃ©sidence bornÃ©es. Ressources incompatibles et shader packs conservent le chemin prÃ©cÃ©dent. Aucun gain FPS annoncÃ© ; tests de propriÃ©tÃ©/invalidation locaux rÃ©ussis, gÃ©omÃ©trie et compilation en CI.
### Work : images dynamiques et propriÃ©tÃ© des buffers

RÃ©sultats de textures invalidÃ©s par gÃ©nÃ©ration, file de complÃ©tions et publications bornÃ©es, recadrage CPU sur worker avec libÃ©ration des images remplacÃ©es. PropriÃ©tÃ© explicite des buffers crÃ©Ã©s par le compilateur Work, fermeture unique des aliases et nettoyage des buffers Ã  la collecte. Tests locaux de capacitÃ©, races et libÃ©ration rÃ©ussis. Correction du nom de matÃ©riau glow dans le test CI du prototype de rails.

### Next : premiers composants d'architecture

Instanciation shaderless GL 3.3 par sÃ©ries compatibles, trois pages bornÃ©es et fences non bloquantes ; VAO sources inchangÃ©s. Cache de poses par vraie image Forge/rÃ©vision de vÃ©hicule. RÃ©ordonnancement des triangles opaques et sommets Ã  l'upload. Les six groupes de tests locaux passent. Cible du redirect vÃ©rifiÃ©e dans le JAR : `BatchManager.lambda$drawBatch$2`, et non `drawBatch` dont le corps appelle une lambda. Les autres grands chantiers restent suivis explicitement dans `docs/mtr-next-version-fr.md`.

### Next â€” compilation de shaders et couverture des modÃ¨les

Suppression des recompilations des trois shaders internes lors de chaque upload, avec invalidation par gÃ©nÃ©ration de ressources et contrÃ´le du succÃ¨s complet. Aucun changement Ã  ShaderInstance.apply / setupShaderBatchState / uniformes Oculus. Tests de succÃ¨s, Ã©chec partiel, gestionnaire distinct, invalidation et option de dÃ©sactivation. RÃ©ordonnancement opaque Ã©tendu aux chemins originaux Blockbench et OBJ isolÃ© ; propriÃ©tÃ© des buffers attachÃ©e aux VAO. Suppression des allocations de tÃ¢ches dâ€™occlusion non utilisÃ©es pendant les ombres. Recherche des candidats triangles par prÃ©sence en cache en O(1), pour limiter le coÃ»t de prÃ©paration. Cibles synthÃ©tiques contrÃ´lÃ©es dans la dÃ©pendance distribuÃ©e ; validation Mixin en jeu encore requise.

Next final vÃ©rifiÃ© : code 8574d7fb788b17e4dd19f0189948475ee260135d, CI 38146219733 rÃ©ussie, six groupes de checks Python et onze tests de gÃ©omÃ©trie. JAR SHA256 746ea60c7a66df2b5ad6698121035413185e5b59c8a25cf460ca5f5c8e2d1b65. Rapports et artefact rÃ©cupÃ©rÃ©s ; documentation des limites et options actualisÃ©e. Aucun benchmark en jeu.

### Next architecture : five requested rendering mechanisms

Oculus 1.8 shader source integration with unchanged full apply and preflight/fallback; early train/car visibility and deferred light; immutable rail/eye-candy regions with selected multi-draw spans; shared-vertex detail index LODs; bounded predictive CPU model preparation. V2 remains unchanged. See docs/mtr-next-architecture-fr.md for scope, switches and outstanding runtime validation.

Next architecture final : code db74325bcdb362322c4df000057a58b38ae98fcf, CI 38153720748 rÃ©ussie ; sept groupes CPU, quinze tests de gÃ©omÃ©trie, comparaison GL logiciel et intÃ©gritÃ©/contrats statiques du JAR rÃ©ussis. Artefact rÃ©cupÃ©rÃ©, SHA256 74816821665f136f46f803aca7659b9740f95521831f7ae54826b96b61ed4786. Validation Minecraft/Oculus/France Addon et FPS restant Ã  effectuer.

Profile-driven follow-up verified: 62db10751f9098daa5d3595ac7ade21b06e4445f, CI 38157947923 passed, eight CPU groups, fifteen geometry tests, real software GL pixels and static JAR contracts. Retrieved JAR SHA256 c4d999713fa5b0e981d51d3a6194484cb7f66824c73dd6566b27a48a6fc05265. No in-game measurement for this binary.

### Six chantiers FPS — implémentation

Texte du monde scoped et buffer privé Forge ; commandes résolues, instances RGBA/light/overlay sous shaderless et Oculus, regroupement shaderless à rectangles disjoints ; matériaux partiellement compatibles et régions multi-pages ; poses de rails persistantes ; interactions et soumission visuelle des wagons séparées ; contraction géométrique bornée et LOD en pixels. Détail et limites : docs/mtr-six-fixes-fr.md. Tests locaux réussis, compilation/rendu logiciel en cours. Aucun gain FPS annoncé.

Préparation des indices de détail déplacée sur deux workers bornés ; modèle original disponible sans attente ; une publication GPU par passe ; annulation/reload et nettoyage GL sur le thread de rendu testés. Comparaison native des pixels shaderless/Oculus sur NVIDIA et Mesa réussie, matériaux forcés prioritaires et VAO sources préservés. Premier échec CI limité à la lecture des lignes repliées du manifeste, corrigé ; candidate 94d334 compile et passe tous ses contrôles. Candidate finale avec préparation asynchrone : f137172, CI 38163655448 réussie. Dix groupes CPU, vingt tests de géométrie, rendu OpenGL et vingt mixins/contrats statiques du JAR vérifiés. Artefacts et logs récupérés. JAR 84 466 558 octets, SHA256 346f5b33dc3e6d7aa756a375edf2295f9fde4c2739ff8f45bee059d872abd52a. Aucun gain FPS annoncé.
