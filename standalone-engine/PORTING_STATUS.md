# Portage Queue Hotpath V2 -> mod Forge autonome

## Référence immuable

Le **seul** point de départ est `work/mtr-engine-queue-hotpath-v2`
(commit de code validé `629ed8fe6b7db18cfd5f20d54249d448abaeaf8e`,
avec documentation ultérieure `6f9d6edaf69515bac95566608b7062424b54422f`).

Ce choix est volontaire : l'utilisateur a mesuré environ **79 FPS AVG**
dans la gare avec Complementary HIGH sur Queue Hotpath V2, contre des
résultats incohérents sur la branche expérimentale suivante
`work/mtr-engine-100fps-cpu-v1`.

Aucun des nouveaux `resolveLazyLookup` / `compiledIdentity` / hash
précalculés de cette expérimentation n'est inclus dans les caches
`standalone-engine/src/main/java/dev/igntv/mtrengine/core/`.

## Déjà recodé comme addon indépendant

- [x] Mod Forge 1.20.1 `mtrengine`, MTR officiel 4.0.5 dépendance externe
- [x] Hook client `MainRenderer`, début/fin du passage rails
- [x] `EngineRenderRails` : rendu personnalisé des rails selon Queue Hotpath V2
- [x] `RailSectorBatcher`, `StaticRegionRenderer`, `StaticDecorationRegions`
- [x] `PersistentRegionCache`, `ScenePlanCache`, `RegionPageLayout`
- [x] `RailBatchGeometry`, `RailGeometrySnapshots`, `ModelMeshCompiler`
- [x] `FastRegionMeshUploader`, `RegionVertexRanges`
- [x] `RailSpatialIndex`, `RailGeometryCache` autonomes
- [x] Adaptateur de source Blockbench/OBJ pour MTR officiel via Mixins
- [x] Accessors `OptimizedModel` et adaptateur `OptimizedModelWrapper`
- [x] Cache des attributs OpenGL / des matrices et Mixins associés
- [x] Batching de texte dans le monde, avec une source de buffers isolée
- [x] Nettoyage des buffers/caches lors des changements de monde et rechargements
- [x] Tests CPU de stabilité et rechargements de régions extraits de la baseline
- [x] Sans `@ModifyArgs` Oculus ni envoi du mod MTR dans le même JAR

## Pas encore externalisé et testé

- [ ] Optimisations de chargement/détails des modèles de véhicules, train culling/LOD
- [ ] Traitement asynchrone et cycles de vie des autres ressources MTR
- [ ] Regroupement par instancing des modèles (Oculus explicitement désactivé)
- [ ] Optimisations spécifiques des panneaux PIDS et du rendu de véhicules
- [ ] Autres mixins de compatibilité shader et ajouts de surveillance GPU
- [ ] Lancement réel avec **MTR officiel**, Embeddium, Oculus et MTR France Addon
- [ ] Comparaison visuelle des courbes, textures, poses, ombres et ressources
- [ ] Benchmark 60 s reproductible : MTR stock vs fork 79 FPS vs addon

**Ce mod n'a pas encore prouvé le maintien des 79 FPS dans Minecraft.**
Le flag `-Dmtr.engine.addon.regions=true` active un chemin expérimental.
Sans le flag, les rails continuent d'utiliser le renderer MTR officiel.

## Sécurité

Ne pas installer le fork intégré et le MTR officiel simultanément. Installer
uniquement **MTR officiel 4.0.5 + MTR Engine addon**, dans une copie de l'instance
et sur un monde sauvegardé. Rétablir les JAR précédents si le client refuse
de démarrer, si les rails deviennent invisibles ou si les performances régressent.

Le succès du build CI signifie compilation, tests Java et intégrité du JAR,
**pas** validation runtime avec Oculus ou MTR France Addon.
