# Prochaine version : renderer MTR

Chantier demandé le 11 octobre 2026 : intégrer les grandes architectures étudiées, puis essayer la prochaine version directement. La V2 et le précédent JAR Work restent des références intactes. Ce document suit le code réel ; il ne présente pas les quatorze architectures comme achevées.

## Premier lot

- Instanciation GL 3.3 sur les séries consécutives d'au moins quatre dessins du même VAO opaque, avec couleur, lumière et overlay identiques. Shaderless seulement, pas d'instanciation sous Oculus. Ordre des dessins conservé. Matrices par instance et géométrie source partagée.
- Vues de matrices préallouées et tableaux CPU des pages réutilisés après croissance : pas de nouveau tableau de matrices pour chaque groupe à chaque image.
- Trois pages de transfert par géométrie, fences interrogées avec timeout zéro, retour aux dessins ordinaires si toutes les pages sont occupées. Les données identiques sont réutilisées sans upload. Pas de persistent mapping ni de compute culling.
- Au maximum 64 géométries résidentes, 1024 instances par dessin, trois pages de 64 Kio par géométrie ; plafond des nouveaux buffers d'instances : 12 Mio. Les VAO sources restent intacts, les VAO et buffers ajoutés sont libérés sur éviction/reload/changement de monde.
- Cache des positions, rotations et lumières de voitures/bogies limité à une image Forge. Identité du véhicule et révision de simulation/données sont vérifiées. Une passe d'ombres antérieure à la simulation principale ne fournit pas une pose périmée à celle-ci. Ne pas confondre cache de pose avec arrêt de simulation.
- Réordonnancement borné des triangles opaques et des sommets à la compilation : mêmes triangles, ordre de leurs trois sommets conservé, tous les attributs conservés, sources non modifiées. Heuristique Java originale, pas intégration native de meshoptimizer. Son estimation de cache est distincte du coût GPU mesuré.
- Optimisation également appliquée aux uploads Blockbench et OBJ isolés par le chemin original RawModel.upload, sans modifier ses groupes de matériaux. Les primitives transparentes/lumineuses restent exclues du réordonnancement.
- Compilation des trois shaders internes une seule fois par gestionnaire et génération de ressources. Un chargement de modèle ne recompilera plus un jeu complet et valide. Échec partiel, autre gestionnaire ou reload : nouvelle compilation. L’application par dessin et les uniformes Oculus restent complets.
- Les passes d’ombres ne construisent plus les tâches de visibilité qui étaient abandonnées ensuite ; seule la passe principale les transmet au worker.
- Suivi de propriété également appliqué au chemin de fusion OBJ ; nettoyage des buffers et VAO si une construction échoue.

## Options indépendantes

| Option | Défaut | Effet |
| --- | --- | --- |
| `-Dmtr.next.instances=false` | true | Désactive l'instanciation ; shader packs utilisent toujours les dessins ordinaires. |
| `-Dmtr.next.poseCache=false` | true | Recalcule les poses à chaque passe. |
| `-Dmtr.next.shaderReloadCache=false` | true | Rétablit les recompilations répétées des shaders internes lors des uploads. |
| `-Dmtr.next.meshOrder=false` | true | Conserve l'ordre des indices/sommets. |
| `-Dmtr.v4.metrics=true` | false | Compteurs existants et groupes/instances/transferts/replis du nouveau backend. |
| `-Dmtr.v4.gpuMetrics=true` | false | Intervalles GPU asynchrones existants. |
| `-Dmtr.v4.railBatches=true` | false | Prototype précédent de secteurs de rails ; option séparée. |

## Validation

Les tests locaux enregistrent les frontières GL : matrices et offsets, réutilisation sans upload, saturation des trois pages sans attente, plafonds, ordre, lumière différente, shader pack, source fermée, fermeture des seules ressources possédées. Le cache de pose est testé aux frontières d'image et de révision. Des tests JUnit contrôlent la géométrie réelle de mappings. La CI doit compiler Forge et contrôler les méthodes synthétiques réelles ciblées par le mixin.

La compilation seule ne valide pas l'application des nouveaux mixins ni les shaders en jeu. Aucun gain FPS n'est encore mesuré. Référence de comparaison : [protocole](mtr-benchmark-fr.md).

## Architectures encore distinctes du premier lot

Les régions persistantes complètes, l'arène de géométrie/multi-draw, la sélection GPU/Hi-Z, l'intégration des instances Oculus, la compilation parallèle/cache disque/préchargement prédictif, les LOD, la sélection d'ombres et la refonte des affichages nécessitent encore leur implémentation et validation. Le transfert en anneau et le cache de poses ne doivent pas être présentés comme ces moteurs complets.

## Portée des quatorze pistes

| Piste étudiée | État réel dans la prochaine version |
| --- | --- |
| Régions persistantes | Prototype de secteurs de rails précédent, optionnel ; pas une refonte générale. |
| Instanciation | Implémentée pour séries opaques compatibles, hors shaders et ombres. |
| Arènes / multi-draw | Non implémentées : le partage des buffers d’instances n’est pas une arène géométrique. |
| Sélection GPU / indirect | Non implémentée. |
| Visibilité hiérarchique | Non implémentée ; occlusion existante conservée, tâches inutiles d’ombres supprimées. |
| Hi-Z / occlusion logicielle nouvelle | Non implémentées. |
| Instances compatibles Oculus | Non implémentées ; voie ordinaire conservée. |
| Pose commune aux passes | Implémentée, bornée à une image et à la révision du véhicule. |
| Compilation parallèle / disque / préchargement | Non implémentés ; recompilations redondantes de shaders supprimées séparément. |
| Indices / ordre des sommets | Implémentés sur géométrie opaque compatible, heuristique Java bornée. |
| LOD / clusters | Non implémentés. |
| Sélection / LOD d’ombres | Non implémentés, aucun train volontairement retiré des ombres. |
| Atlas et affichages | Files d’upload et cache PIDS précédents conservés ; pas de nouvel atlas. |
| Streaming GPU | Trois pages bornées et fences sans attente pour les instances ; pas de mapping persistant. |

Cette version permet un essai des mécanismes réellement intégrés. Elle ne termine pas la demande initiale d’intégrer les quatorze architectures. Une compilation réussie ne justifie pas d’annoncer une compatibilité en jeu ou un gain par rapport à V2.
