# Candidat six chantiers FPS — Forge 1.20.1

La branche `work/mtr-v4-optimization` poursuit le candidat `62db107`. V2, V3 et V4 restent intactes. La version interne 4.0.5 reste identique pour les addons. Aucun gain FPS n'est mesuré ici.

## Changements effectifs

| Chantier | Implémentation | Limites et repli |
|---|---|---|
| Texte du monde | Les trois surcharges GraphicsHolder.drawText utilisent un buffer privé au sein d'un callback MTR. Matrices/lumières sont appliquées à chaque glyph ; vidage avant dessin non textuel, sortie de callback et exception. | GUI inchangée. Les changements de RenderType/font peuvent encore vider le buffer. Aucun cache de textures de panneaux ni regroupement entre callbacks inconnus. |
| Commandes et instances | Résolution du VAO/LOD, visibilité et bornes une seule fois par commande. Matrice, RGBA, lightmap et overlay propres à chaque instance ; valeurs forcées du matériau prioritaires. Oculus reçoit ces attributs par tableaux de huit éléments et conserve son application complète. | Transparence, matériaux sans profondeur, buffers incompatibles, GL4ES et programmes inconnus : dessins ordinaires. Programmes avec images/storage/identifiants de draw ou stages supplémentaires : pont shader rejeté. |
| Formation de lots | Sans shader pack, recherche limitée à 24 commandes et regroupement uniquement si les rectangles conservateurs à l'écran ne touchent aucun dessin traversé. | Ordre conservé pour les surfaces qui se recouvrent, les bornes inconnues et Oculus. Pas de tri global par matériau. |
| Régions | Partition des matériaux compatibles, découpage des grands meshes par triangles avec remappage des sommets, plusieurs pages de 16 000 sommets par secteur. Placement stable malgré changement de lumière/visibilité. Parties restantes gardent leurs VAO d'origine. | Transaction par objet : si une page manque, l'objet entier suit son chemin précédent sans doublon. Une construction par passe, 256 000 sommets GPU, 64 pages et 200 000 sommets CPU retenus. Réinitialisation des métadonnées trop nombreuses. |
| Rails | Cache borné de 8 192 poses de segments : orientations, matrice, transformation de repli et identité ne sont plus reconstruits à chaque passe. | Coordonnées/resource/flip font partie de la clé. Monde/reload invalident le cache. Lumière et sélection restent actualisées ; les styles et leurs résolutions ne sont pas gelés. |
| Trains | Sélection visuelle séparée de la portée d'interaction. Wagons proches hors champ : portes, sons, embarquement et déplacement maintenus, corps/bogies/animations/passagers visuels non soumis. Interactions du joueur uniquement en passe principale. | Frustum absent ou metadata invalide : repli. Les trains montés restent conservateurs. Les points de raccordement sont maintenus pour les voitures traitées. Aucun culling d'occlusion par GPU ni budget qui ferait disparaître un train. |
| Géométrie | Réduction de meshes connectés par contraction d'arêtes vers des sommets existants. Bordures, coutures, couleur/lumière, topologie, orientation et gradients UV protégés. Buffers de sommets originaux partagés ; indices supplémentaires bornés à 8 Mio. | Maximum 20 000 sommets/40 000 faces pour le simplificateur. Repli sur suppression de composants minuscules, puis modèle complet. Les modèles déjà minimaux peuvent ne rien gagner. |
| Choix du détail | Projection réelle, hauteur du viewport, distance moins rayon du modèle, erreur géométrique et budget .75 pixel en principal/1 pixel en ombre. Projection lue une fois par passe. Portes optimisées marquées TRAIN avec distance correcte. | Aucun remplacement près du modèle ou avec projection invalide. Déformations d'un shader pack : LOD désactivé par défaut ; `mtr.next.shaderLod=true` est une expérimentation explicite, pas une garantie de fidélité. |

## Contrôles

Les tests CPU existants passent, avec nouveaux cas de packing RGBA/shorts signés, changement de lumière sans fragmentation, renouvellement des pages, capacité, FOV/résolution/proximité, ordre des dessins qui se recouvrent et bornes inconnues, exceptions imbriquées de texte. Les tests Forge couvrent la réduction d'un mesh connecté, conservation de sa surface/bordure/UV, erreurs et discontinuités UV. Le test OpenGL logiciel compare les pixels ordinaires/regroupés avec huit matrices, couleurs, lumières et overlays différents, puis des dessins ordinaires après restauration.

La compilation Forge et les résultats exacts du candidat seront consignés après GitHub Actions. Les tests de source/bytecode et le rendu logiciel ne remplacent pas une partie avec Oculus, le shader choisi et MTR France Addon. Aucun monde privé n'est demandé.

Les mesures utilisateur disponibles concernent le précédent JAR db74325 : 45–50 FPS avec train en gare et shaders, 60–70 sans train ; généralement 90–100 sans shaders. Les captures Spark ne permettent pas d'annoncer le FPS de ce candidat ou sa victoire sur V2. Le coût du profiler a perturbé le dernier test : comparer les versions sans profiler dans la même scène, puis capturer uniquement si nécessaire.

## Interrupteurs de comparaison

`-Dmtr.next.textBatch=false`, `-Dmtr.next.instances=false`, `-Dmtr.next.oculusInstances=false`, `-Dmtr.next.grouping=false`, `-Dmtr.next.regions=false`, `-Dmtr.next.visibility=false`, `-Dmtr.next.lod=false`. Ils permettent d'isoler la cause d'un défaut ou d'une régression. Les appels complets du shader/Framebuffer de V3 ne sont pas supprimés.

Source primaire du contrat d'attributs Oculus : [VanillaTransformer à la révision inspectée](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/VanillaTransformer.java). Les cibles MTR/Minecraft-Mappings sont vérifiées contre le bytecode embarqué, pas seulement contre l'upstream actuel.
