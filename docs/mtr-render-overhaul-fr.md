# MTR Engine — refonte du rendu V2/V4 avec Complementary

## Origine : mesures du 12 octobre 2026

- Carte Superflat de test avec rails, station et métros, 12 chunks,
  6 Gio RAM, Forge 1.20.1, Embeddium 0.3.31, Oculus 1.8.0,
  Complementary Reimagined 5.9.3.
- La V2 avait ~51 FPS AVG sur la vue avec trains ; le premier Max Perf
  ~34 FPS. Les deux relevés restent des compteurs FPS de séquences
  potentiellement différentes : ce ne sont pas des comparaisons A/B certifiées.
- L'utilisateur a confirmé ~30 FPS AVG avec `-Dmtr.next.regions=false`.
  Il est donc **faux** de considérer le cache de régions comme unique cause.
- Spark montre de fortes dépenses inclusives dans `BatchManager.drawAll`,
  `VertexAttributeState.apply`, `StaticRegionRenderer.build`
  et `RawMesh.distinct`. Spark ne chronomètre pas la durée réelle GPU.

## Modifications réelles de cette branche

1. **Mode édition** : l'ancien parcours de 33³ = **35 937 blocs**
   par image et par passe Ombres est remplacé par un index temporaire
   de positions candidates de nœuds (TTL 250 ms). Les états de connexion
   restent lus en direct sur les candidats et le cache est réinitialisé
   au changement de monde ou de position du joueur. Les véritables
   rails et nœuds restent visibles. Test de désactivation :
   `-Dmtr.engine.editorCache=false`.
2. **Shadow passes** : aucun graphe de tâches d'occlusion n'est alloué
   lorsque ces tâches ne seront jamais exécutées. Les surbrillances
   d'édition ne produisent plus de marqueurs artificiels dans la passe
   d'ombres. Les rails physiques sont toujours rendus dans les ombres.
3. **Oculus** : tant que la variante GLSL instanciée n'a **pas réellement
   été construite et liée**, MTR court-circuite le parcours coûteux de
   regroupement `ConsecutiveRuns` qui n'apportait aucun gain puisque
   le fallback devait rendre les appels un par un. La modification
   dangereuse `@ModifyArgs` reste désactivée : pas de `Args$1`.
   Ce build **ne prétend pas restaurer l'instancing sous Oculus**.
4. **Maillages opaques statiques** : l'uploader des régions écrit les
   attributs et index directement, sans exécuter
   `RawMesh.distinct()` (HashMap de sommets + HashSet de triangles).
   Les matériaux particuliers/formats inattendus utilisent l'uploader
   classique. Cette voie n'est appelée que lors d'une (re)construction
   de régions opaques, jamais pour les modèles dynamiques des addons.
   Elle peut augmenter le nombre de sommets chargés en VRAM, tout en
   réduisant les allocations et le CPU lors des constructions.
5. **Résidence GPU** : pages de régions 64 → 256 par défaut,
   budget de 256k → 1M sommets, plans de scènes 8192 → 32768
   et poses de rails 8192 → 32768. Budgets bornés, non illimités.
   L'objectif est d'éviter le cycle reconstruction/éviction dans
   les cartes artificiellement denses, pas de forcer le GPU à tout
   dessiner.
6. **Profilage intégré** : `-Dmtr.engine.phaseMetrics=true` affiche
   dans `logs/latest.log` toutes les 10 secondes les temps CPU moyens
   et cumulés, en séparant rendu principal et passes d'ombres :
   `VEHICLES`, `RAILS`, `REGION_FLUSH`, `BUILD_QUEUES`, `SUBMIT`.
   Il s'agit du mur CPU, pas du GPU. Pour le GPU, conserver les
   requêtes GL asynchrones du logger `-Dmtr.v4.gpuMetrics=true`.

## Options JVM indépendantes (comparaison scientifique)

- `-Dmtr.engine.editorCache=false` : ancien scan de l'éditeur.
- `-Dmtr.engine.editorCacheMs=250` : TTL des candidats.
- `-Dmtr.engine.fastRegionUpload=false` : ancien upload avec déduplication.
- `-Dmtr.engine.regionPages=256` : nombre de pages en résidence.
- `-Dmtr.engine.regionVertices=1000000` : budget de sommets.
- `-Dmtr.engine.sceneLocations=32768` : budget de clés de plans.
- `-Dmtr.engine.railPoses=32768` : budget de poses de rail.
- `-Dmtr.next.regions=false` : toutes régions désactivées.
- `-Dmtr.v4.metrics=true` et `-Dmtr.engine.phaseMetrics=true` :
  diagnostics à utiliser dans un second passage, **pas pour les FPS
  de référence** (les compteurs ajoutent leur propre surcoût).

## Ordre des tests

Conserver un JAR MTR unique. Ne pas modifier les autres mods, shaders
ou le monde entre les runs. Démarrer la nouvelle branche, vérifier
rails, sidings, couleurs d'édition, tracés de métros, portes, ombres,
textures et absence de crash. Attendre le chargement complet.

Au même emplacement/caméra, comparer sur **60 s** avec Complementary :
outil rail en main, main vide, dos au réseau, vue générale, métros
arrivant. Capturer moyenne, minimum et 1% low si possible.
Effectuer ensuite **un** passage avec diagnostics CPU/GPU pour savoir
si l'objectif CPU→GPU est atteint.

**Aucune hausse de FPS, absence de régression ni compatibilité complète
avec tous les addons n'est déduite du fait que GitHub compile.**
L'instancing shader pack reste à résoudre sans injection fragile.
