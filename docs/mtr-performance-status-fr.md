# État des quarante pistes

La prochaine version est décrite dans [son suivi](mtr-next-version-fr.md). Code Next : `91c09ff5a4fa970f33316c906f9ed2af574867ef`. Les validations en jeu et la comparaison V2 restent à effectuer. Les mentions ci-dessous ont été mises à jour pour les mécanismes Next ; les preuves du précédent artefact restent conservées.

Code compilé : `500334ef0e70f67d6f57617cdd0b1e01a495cbac`. [CI 38140845544](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38140845544) réussie. Ces statuts décrivent le code, **pas** des gains FPS mesurés. Aucune piste n'est déclarée entièrement validée en jeu.

| Piste | Réalisation actuelle |
| --- | --- |
| 01 — Rails persistants par secteur | Prototype compilé, opt-in shaderless ; sélection visible conservée. |
| 02 — Décors fixes regroupés par secteur | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 03 — Fusion complète des groupes de train compatibles | Partielle : groupes OBJ/Blockbench opaques égaux d'un même modèle/condition. |
| 04 — Batches réels de soumission | Partielle : maillages opaques communs et secteurs de rails ; pas de renderer général de lots. |
| 05 — Instanciation des éléments répétés | Implémentée Next : séries consécutives de VAO opaques compatibles, quatre occurrences minimum, shaderless hors ombres, GL 3.3 ; validation en jeu à faire. |
| 06 — Arènes de buffers GPU partagées | Pas implémentée ; propriété, budget et fragmentation à mesurer. |
| 07 — Multi-draw des lots compatibles | Dépend des arènes, formats et états compatibles ; pas implémenté. |
| 08 — Sélection et commandes pilotées par GPU | Expérimental, pas implémenté. |
| 09 — Streaming GPU sans attendre les buffers en cours | Partielle Next : trois pages d’instances, réutilisation des données identiques, fences sans attente et repli si saturation ; pas de mapping persistant. |
| 10 — Regroupement des textures et matériaux | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 11 — Une pose de train partagée entre passes | Implémentée Next : cache limité à une image Forge et révision de simulation/données ; restauration du rail progress par finally. Validation main/shadow en jeu à faire. |
| 12 — Application Oculus complète au niveau du lot | Fusion des dessins en amont seulement ; intégration dédiée pas implémentée. |
| 13 — Uniformes dérivés recalculés seulement si modifiés | Pas de cache ajouté ; application complète préservée. |
| 14 — Visibilité dédiée aux objets qui projettent des ombres | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 15 — Maillages simplifiés pour les ombres | Compromis visuel optionnel, pas implémenté. |
| 16 — Hiérarchie de visibilité par région/train/voiture | Pas implémentée ; l'index spatial V4 existant reste distinct. |
| 17 — Élimination grossière avant les calculs coûteux | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 18 — Snapshots d'opacité par section | Pas ajoutés ; le cache d'opacité existant n'est pas un snapshot immuable. |
| 19 — Culling incrémental avec budget et fraîcheur | Fondations de stabilité et budget de construction de secteurs ; culling incrémental complet pas ajouté. |
| 20 — Occlusion GPU avec profondeur hiérarchique | Expérimental, pas implémenté. |
| 21 — Visibilité par salles et ouvertures | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 22 — Niveaux de détail selon la taille à l'écran | Compromis visuel optionnel, pas implémenté. |
| 23 — Optimisation des maillages sans réduire le détail | Implémentée Next : triangles opaques et ordre de sommets réorganisés, attributs et winding conservés ; estimation de cache distincte du coût GPU. |
| 24 — Compilation des transformations et animations | Partielle : transformations de rails baked dans le secteur ; animations générales intactes. |
| 25 — Cache disque des modèles compilés | Conditionnelle, à implémenter et valider selon les nouvelles mesures. |
| 26 — Préchargement prédictif des trains approchants | Pas ajouté ; préchargement configurable existant conservé. |
| 27 — Préparation CPU parallèle des modèles | Recadrage d'images sur worker existant ; compilation parallèle des modèles pas ajoutée. |
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
