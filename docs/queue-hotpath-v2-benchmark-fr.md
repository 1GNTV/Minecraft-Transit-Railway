# MTR Engine — Queue Hotpath V2 (après les 70 FPS)

Référence conservée : branche `work/mtr-engine-render-overhaul`, build Forge validé
et **70 FPS AVG** rapportés dans la gare d'essai avec Complementary HIGH, régions actives.
Cette valeur est une mesure utilisateur, pas une garantie sur la présente branche.

## Changements mesurables

- `PersistentRegionCache.get` : quand le GPU possède déjà tous les fragments
  visibles, il réutilise directement la page **sans réécrire
  `entry.content`**. Évite les insertions `LinkedHashMap` dans chaque passe,
  notamment dans les cascades d'ombres. Toute nouvelle `Selection`
  (géométrie, lumière ou matrice modifiées) invalide ce raccourci et déclenche
  le traitement habituel. Le test `tools/test_region_cache_hotpath.py`
  couvre 50 frames stables, un changement de lumière, la réapparition
  de fragments et l'extension d'une page.
- `StaticRegionRenderer.queue` : l'ancienne `new Matrix4f(matrix)` et ses
  trois mutations de translation se produisaient **sur chaque rail et passe**.
  On compare maintenant les 16 composantes de la matrice directement,
  avec translation calculée par des primitives. Une matrice copiée est
  créée uniquement si le plan nécessite une (re)construction.
- Diagnostics `-Dmtr.engine.metrics=true` : le log affiche dorénavant
  `compiled-hits`, `page-builds`, `evictions`, `resident-vertices`,
  et `rail pose lookup hits/misses` toutes les 10 s. Désactivé par défaut.
- Les limites de pages GPU, le format du rendu et la sélection de fragments
  de la branche 70 FPS restent **inchangés**.

## Comment comparer

1. Conserver une copie du JAR de référence `work/mtr-engine-render-overhaul`.
2. Ne mettre **qu'un seul JAR MTR** dans le dossier `mods` de l'instance.
3. Ne pas ajouter `-Dmtr.next.regions=false` : la nouvelle optimisation
   cible justement le chemin des régions persistantes.
4. Conserver Complementary HIGH, la même distance d'affichage,
   la même gare, la même caméra, les mêmes trains et les mêmes addons.
5. Attendre la stabilisation des chargements, mesurer 60 s sans outils
   de diagnostic, puis comparer FPS AVG et 1 % low.
6. Dans une deuxième session : `-Dmtr.engine.metrics=true`,
   `-Dmtr.engine.phaseMetrics=true`, et si besoin
   `-Dmtr.v4.gpuMetrics=true`. Lire `logs/latest.log`
   en distinguant temps CPU du thread Java et temps GPU.

Le build et les tests Java sont des validations techniques, **pas** une preuve
de meilleures performances Minecraft. Si les FPS baissent, revenir au JAR
de référence ; c'est l'intérêt de cette branche dédiée.

## Instancing Oculus

L'instancing GLSL d'Oculus reste volontairement **désactivé sur Forge** :
la précédente injection `@ModifyArgs` a provoqué un crash au lancement
(`Args$1`). Les changements ci-dessus sont indépendants de cette
fonctionnalité. Il serait faux de présenter ce JAR comme une restauration
de l'instancing Oculus ; l'expérimentation shader doit être isolée dans
une branche distincte et validée en runtime.
