# MTR Engine — Max Perf (Forge 1.20.1)

## Objet

Accélérer la gare dense sans sacrifier les shaders, les portes, les interactions,
les fonds de framebuffer Oculus, ni les points d'injection d'addons.

Branche `work/mtr-engine-max-perf`, issue de
`work/mtr-engine-persistent-scene`, qui contient le correctif binaire TJMetro.
La version interne de MTR reste 4.0.5. **Ne pas mélanger deux JAR MTR.**

## Changements réellement livrés

### 1 — CPU : régions statiques persistantes

Le cache de scène E0 précédent conserve plans CPU et masques visibles.
Cette branche recycle en plus les collections temporaires utilisées pour
rassembler les pages demandées et les plages de matériaux. Les caches GPU
gardent l'unique responsabilité de leurs ressources GL ; la réinitialisation
des pages déclenche le rendu original pendant cette passe.

### 2 — Correctif à forte valeur pour les grandes gares

`PersistentRegionCache.get` additionnait auparavant le nombre de membres
conservés et le nombre de membres visibles, **même lorsque c'étaient les
mêmes membres**. Une gare de 300 pièces identiques pouvait donc réinitialiser
sa table de membres à répétition.

Le plafond de 512 porte maintenant sur le nombre **réel de membres uniques**.
Un test dédié couvre 450 pièces réparties sur plusieurs sélections successives,
la résidence du maillage compilé et la libération exacte des ressources.

### 3 — Rails : pose stable et consultation sans allocations

`RailSectorBatcher.submit` ne crée plus un nouvel objet de clé pour chaque
segment **déjà connu** à chaque rendu. Une clé de recherche réutilisable est
comparée aux clés immuables de la table LRU (8192 poses maximum). Les valeurs
de lumière continuent d'être appliquées par le rendu de la région et ne sont
pas figées dans la pose. Vidage au reload/changement de monde.

### 4 — RTX et autres GPU : multi-draw indirect compatible

`RegionVertexRanges` conserve l'ancien `glMultiDrawElements` pour les
shaders Oculus, les ombres, les GPU ne disposant pas d'OpenGL 4.3,
les environnements GL4ES et les usages non pris en charge.

Pour des pages opaques shaderless avec **au moins 8 plages** (réglable),
un buffer GPU de commandes `DrawElementsIndirectCommand` peut être conservé
et soumis par `glMultiDrawElementsIndirect`. Les commandes sont renouvelées
si la visibilité change, pas à chaque image. Les buffers indirects sont
libérés avec les VAO détenus par le cache, et l'ancien binding GL est restauré
après chaque opération.

Ce chemin **n'utilise pas CUDA** : il passe par OpenGL et profite des RTX,
AMD et Intel compatibles. Ce n'est pas encore du culling calculé par compute
shader ; les choix visibles et l'ordre des matériaux restent décidés par les
chemins MTR sûrs. La nouvelle voie ne doit pas être annoncée comme GPU-driven
complet, ni comme une augmentation FPS mesurée.

## Désactivation indépendante par paramètre JVM

| Option | Effet de la valeur `false` |
| --- | --- |
| `-Dmtr.engine.indirect=false` | Rendu des pages via multi-draw existant |
| `-Dmtr.engine.railLookup=false` | Allocation normale des clés de rails (comparaison) |
| `-Dmtr.engine.scenePlans=false` | Désactivation du cache CPU des plans de scène |
| `-Dmtr.engine.visibilityMasks=false` | Reconstitution systématique des plages par matériau |
| `-Dmtr.next.regions=false` | Désactivation complète des régions et retour au renderer original |

`-Dmtr.engine.indirectMinSpans=8` définit le seuil minimum de plages
pour l'indirect, 8 par défaut ; valeur minimale effective 2.

Les propriétés sont lues au chargement des classes : **redémarrer Minecraft**
après modification. Aucun paramètre ne change les données du monde.

## Tests automatiques

- `tools/test_gpu_indirect.py` : compile la classe de production contre GL
  instrumenté et vérifie packing, indices, reupload, fallback shaders/ombres,
  restauration des états et libération GPU.
- `tools/test_dense_regions.py` : 450 objets immuables, sélection alternée,
  pas de réinitialisation abusive ni de reconstruction forcée.
- `tools/test_rail_lookup_engine.py` : 500 soumissions répétées, égalité des
  clés, indépendance de la lumière et invalidation des poses.
- Tests E0 préexistants : plans de scène, changements de ressources,
  shaders GLSL/Oculus via Mesa, géométrie et bytecode/addon contracts.
- GitHub Actions construit Forge 1.20.1 et produit un JAR shaded si tout passe.

## Validation en jeu — obligatoire pour appeler cette version stable

Installer un seul JAR de cette branche dans un profil Minecraft de test.
Conserver TJMetro 1.1.0, MTR France Addon, JCM et Oculus/shader pack.
Contrôler démarrage et arrivée d'un train, bogies, portes, connexions,
affichages défilants, vitres, ombres, écrans, `F3+T`, changement de monde,
et édition de rails.

Comparer V2, branche E0 puis Max Perf dans la **même** gare, avec et sans shaders.
Capturer des séquences sans profiler (minimum 60 secondes, idéalement trois
répétitions) et comparer FPS médian, frametimes et 1% lows. Les rapports Spark
sont utiles après pour comprendre les divergences, mais modifient eux-mêmes la
charge du thread de rendu.

**La compilation et des tests de simulation GL ne constituent pas un lancement
réel du modpack, une garantie universelle de compatibilité ou une preuve de gain.**
