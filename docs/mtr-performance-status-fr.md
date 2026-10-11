# État des quarante pistes

Les nouveaux cinq chantiers sont détaillés dans [Next architecture](mtr-next-architecture-fr.md), avec compilation en cours et validation en jeu à faire. La précédente version est décrite dans [son suivi](mtr-next-version-fr.md). Code Next : `8574d7fb788b17e4dd19f0189948475ee260135d`. Les validations en jeu et la comparaison V2 restent à effectuer. Les mentions ci-dessous ont été mises à jour pour les mécanismes Next ; les preuves du précédent artefact restent conservées.

Référence précédente compilée : `500334ef0e70f67d6f57617cdd0b1e01a495cbac`. [CI 38140845544](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38140845544) réussie. Ces statuts décrivent le code, **pas** des gains FPS mesurés. Aucune piste n'est déclarée entièrement validée en jeu.

| Piste | Réalisation actuelle |
| --- | --- |
| 01 — Rails persistants par secteur | Régions de contenu persistantes : sélection de plages d’indices sans recompiler les sommets sur rotation de caméra ; validation en jeu à faire. |
| 02 — Décors fixes regroupés par secteur | Régions persistantes pour les Eye Candy solides compatibles, collections main/shadow séparées ; repli individuel. |
| 03 — Fusion complète des groupes de train compatibles | Partielle : groupes OBJ/Blockbench opaques égaux d'un même modèle/condition. |
| 04 — Batches réels de soumission | Partielle : maillages opaques communs et secteurs de rails ; pas de renderer général de lots. |
| 05 — Instanciation des éléments répétés | GL 3.3 shaderless conservé ; intégration Oculus 1.8.0 ajoutée pour les programmes deux stages et groupes de 4–8 dessins opaques compatibles. |
| 06 — Arènes de buffers GPU partagées | Pas implémentée ; propriété, budget et fragmentation à mesurer. |
| 07 — Multi-draw des lots compatibles | Multi-draw des plages de régions solides ; pas de multi-draw indirect général. |
| 08 — Sélection et commandes pilotées par GPU | Expérimental, pas implémenté. |
| 09 — Streaming GPU sans attendre les buffers en cours | Partielle Next : trois pages d’instances, réutilisation des données identiques, fences sans attente et repli si saturation ; pas de mapping persistant. |
| 10 — Regroupement des textures et matériaux | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 11 — Une pose de train partagée entre passes | Implémentée Next : cache limité à une image Forge et révision de simulation/données ; restauration du rail progress par finally. Validation main/shadow en jeu à faire. |
| 12 — Application Oculus complète au niveau du lot | Application complète du shader Oculus conservée par groupe compatible, matrices modèle/inverse/normales par instance ; preflight et repli. |
| 13 — Uniformes dérivés recalculés seulement si modifiés | Pas de cache ajouté ; application complète préservée. |
| 14 — Visibilité dédiée aux objets qui projettent des ombres | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 15 — Maillages simplifiés pour les ombres | LOD d’indices des petites composantes isolées pour les ombres lointaines ; géométrie restante exacte. |
| 16 — Hiérarchie de visibilité par région/train/voiture | Union train puis voitures sur le frustum de passe, avant ressources/lumière ; proximité et train monté préservés. Pièces shaderless au clip conservateur. |
| 17 — Élimination grossière avant les calculs coûteux | Préparation géométrique sans lumière puis sélection train/voiture avant ressources, éclairage et préparation visuelle. |
| 18 — Snapshots d'opacité par section | Pas ajoutés ; le cache d'opacité existant n'est pas un snapshot immuable. |
| 19 — Culling incrémental avec budget et fraîcheur | Fondations de stabilité et budget de construction de secteurs ; culling incrémental complet pas ajouté. |
| 20 — Occlusion GPU avec profondeur hiérarchique | Expérimental, pas implémenté. |
| 21 — Visibilité par salles et ouvertures | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 22 — Niveaux de détail selon la taille à l'écran | LOD distant à seuils de distance réglables, suppression de petites composantes ; pas de LOD continu universel selon pixels. |
| 23 — Optimisation des maillages sans réduire le détail | Implémentée Next : triangles opaques et ordre de sommets réorganisés, attributs et winding conservés ; estimation de cache distincte du coût GPU. |
| 24 — Compilation des transformations et animations | Partielle : transformations de rails baked dans le secteur ; animations générales intactes. |
| 25 — Cache disque des modèles compilés | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 26 — Préchargement prédictif des trains approchants | Préparation de modèles et cache progressif des trains proches/approchants, fenêtres et files bornées. |
| 27 — Préparation CPU parallèle des modèles | Workers pour Blockbench et géométrie OBJ/MQO depuis snapshots, atlas/finalisation/GL sur le render thread. |
| 28 — Caches résidents pilotés par budget | Partielle : secteurs bornés en sommets/entrées, 64 slots d’instances et 12 Mio maximum pour leurs buffers ; pas de gestionnaire VRAM global. |
| 29 — Géométrie partagée entre variantes | Propriété des aliases suivie ; partage géométrique entre variantes pas ajouté. |
| 30 — Atlas de glyphes et textes mis en cache | Pas ajouté ; le renderer de texte Minecraft existant reste distinct. |
| 31 — PIDS et plans recalculés uniquement sur changement | Cache de recherche automatique V4 conservé ; refonte des layouts pas ajoutée. |
| 32 — Mise à jour partielle des textures dynamiques | Pas implémentée ; publications de textures complètes. |
| 33 — Transferts GPU prioritaires et bornés | Partielle : complétions par fenêtre et construction de secteur par image ; pas de budget global en ms/octets. |
| 34 — File de rendu structurée et réutilisable | Pool de listes existant conservé ; nouvelle file de commandes pas ajoutée. |
| 35 — Simulation client indépendante du nombre d'images | Refonte sensible non implémentée. |
| 36 — Préparation parallèle des poses et données graphiques | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 37 — Éclairage réutilisé avec invalidation locale | Lumière exacte dans la signature de secteur ; cache événementiel pas ajouté. |
| 38 — Deltas réseau et identité des objets conservée | Pas de nouveau protocole ajouté. |
| 39 — Graphe de routes et recherches incrémentales | Core à auditer/profiler ; pas modifié. |
| 40 — Simulation et recherches spatiales à grande échelle | Core à auditer/profiler ; pas modifié. |

S2/S3 : compteurs familles/passes et timestamps GPU optionnels, compilés et testés aux frontières. S4/S5/S7 : générations, replis et propriété ajoutés sur les nouveaux chemins. S1 : protocole et analyse de captures réelles disponibles. S8 : validation visuelle, application live des mixins et comparaison FPS restent à faire.

La [cartographie détaillée](mtr-performance-map-fr.md) donne le mécanisme et le test d'adoption de chaque piste. Le [suivi](work-progress.md) décrit les limites des prototypes ; le [protocole de comparaison](mtr-benchmark-fr.md) conserve les résultats localement.

Next ajoute aussi la compilation des trois shaders internes une seule fois par génération valide de ressources. Ce cache ne remplace ni ShaderInstance.apply ni les uniformes Oculus. Aucun gain FPS n’est déduit de la réussite des tests.

Code compilé : `8574d7fb788b17e4dd19f0189948475ee260135d`. [GitHub Actions 38146219733](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38146219733) réussi : six groupes de checks Python, compilation Forge, onze tests de géométrie (zéro échec/ignoré), contrats statiques de mappings et intégrité du JAR. Logs complets inspectés : aucune erreur, deux warnings Javadoc hérités.

[Artefact Next Forge](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38146219733/artifacts/11704284132), téléchargé et vérifié : 84 365 415 octets, SHA256 `746ea60c7a66df2b5ad6698121035413185e5b59c8a25cf460ca5f5c8e2d1b65`. ZIP SHA256 `bb4a9029531a7040d76bf2a0decacaf98652c6e656853ad680522e2525b4eb38`. Rapports : onze tests, zéro échec/ignoré ; ZIP SHA256 `35da5be9540efe27d0ef2b87a1b946bd370eff0208205f94d7dc294d59755da0`.

Copie locale : `artifacts/MTR-forge-4.0.5+1.20.1-next-8574d7f.jar`. Le nom distingue cet essai ; version interne 4.0.5 conservée. Le précédent JAR Work et la V2 restent intacts. Les commits documentaires ultérieurs ne changent pas ce JAR. Aucune installation ni mesure en jeu n'a été effectuée.
