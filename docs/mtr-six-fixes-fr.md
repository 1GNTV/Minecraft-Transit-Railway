# Candidat six chantiers FPS â€” Forge 1.20.1

La branche `work/mtr-v4-optimization` poursuit le candidat `62db107`. V2, V3 et V4 restent intactes. La version interne 4.0.5 reste identique pour les addons. Aucun gain FPS n'est mesurÃ© ici.

## Changements effectifs

| Chantier | ImplÃ©mentation | Limites et repli |
|---|---|---|
| Texte du monde | Les trois surcharges GraphicsHolder.drawText utilisent un buffer privÃ© au sein d'un callback MTR. Matrices/lumiÃ¨res sont appliquÃ©es Ã  chaque glyph ; vidage avant dessin non textuel, sortie de callback et exception. | GUI inchangÃ©e. Les changements de RenderType/font peuvent encore vider le buffer. Aucun cache de textures de panneaux ni regroupement entre callbacks inconnus. |
| Commandes et instances | RÃ©solution du VAO/LOD, visibilitÃ© et bornes une seule fois par commande. Matrice, RGBA, lightmap et overlay propres Ã  chaque instance ; valeurs forcÃ©es du matÃ©riau prioritaires. Oculus reÃ§oit ces attributs par tableaux de huit Ã©lÃ©ments et conserve son application complÃ¨te. | Transparence, matÃ©riaux sans profondeur, buffers incompatibles, GL4ES et programmes inconnus : dessins ordinaires. Programmes avec images/storage/identifiants de draw ou stages supplÃ©mentaires : pont shader rejetÃ©. |
| Formation de lots | Sans shader pack, recherche limitÃ©e Ã  24 commandes et regroupement uniquement si les rectangles conservateurs Ã  l'Ã©cran ne touchent aucun dessin traversÃ©. | Ordre conservÃ© pour les surfaces qui se recouvrent, les bornes inconnues et Oculus. Pas de tri global par matÃ©riau. |
| RÃ©gions | Partition des matÃ©riaux compatibles, dÃ©coupage des grands meshes par triangles avec remappage des sommets, plusieurs pages de 16 000 sommets par secteur. Placement stable malgrÃ© changement de lumiÃ¨re/visibilitÃ©. Parties restantes gardent leurs VAO d'origine. | Transaction par objet : si une page manque, l'objet entier suit son chemin prÃ©cÃ©dent sans doublon. Une construction par passe, 256 000 sommets GPU, 64 pages et 200 000 sommets CPU retenus. RÃ©initialisation des mÃ©tadonnÃ©es trop nombreuses. |
| Rails | Cache bornÃ© de 8 192 poses de segments : orientations, matrice, transformation de repli et identitÃ© ne sont plus reconstruits Ã  chaque passe. | CoordonnÃ©es/resource/flip font partie de la clÃ©. Monde/reload invalident le cache. LumiÃ¨re et sÃ©lection restent actualisÃ©es ; les styles et leurs rÃ©solutions ne sont pas gelÃ©s. |
| Trains | SÃ©lection visuelle sÃ©parÃ©e de la portÃ©e d'interaction. Wagons proches hors champ : portes, sons, embarquement et dÃ©placement maintenus, corps/bogies/animations/passagers visuels non soumis. Interactions du joueur uniquement en passe principale. | Frustum absent ou metadata invalide : repli. Les trains montÃ©s restent conservateurs. Les points de raccordement sont maintenus pour les voitures traitÃ©es. Aucun culling d'occlusion par GPU ni budget qui ferait disparaÃ®tre un train. |
| GÃ©omÃ©trie | RÃ©duction de meshes connectÃ©s par contraction d'arÃªtes vers des sommets existants. Bordures, coutures, couleur/lumiÃ¨re, topologie, orientation et gradients UV protÃ©gÃ©s. Buffers de sommets originaux partagÃ©s ; indices supplÃ©mentaires bornÃ©s Ã  8 Mio. | Maximum 20 000 sommets/40 000 faces pour le simplificateur. Repli sur suppression de composants minuscules, puis modÃ¨le complet. Les modÃ¨les dÃ©jÃ  minimaux peuvent ne rien gagner. |
| Choix du dÃ©tail | Projection rÃ©elle, hauteur du viewport, distance moins rayon du modÃ¨le, erreur gÃ©omÃ©trique et budget .75 pixel en principal/1 pixel en ombre. Projection lue une fois par passe. Portes optimisÃ©es marquÃ©es TRAIN avec distance correcte. | Aucun remplacement prÃ¨s du modÃ¨le ou avec projection invalide. DÃ©formations d'un shader pack : LOD dÃ©sactivÃ© par dÃ©faut ; `mtr.next.shaderLod=true` est une expÃ©rimentation explicite, pas une garantie de fidÃ©litÃ©. |

## ContrÃ´les

Les tests CPU existants passent, avec nouveaux cas de packing RGBA/shorts signÃ©s, changement de lumiÃ¨re sans fragmentation, renouvellement des pages, capacitÃ©, FOV/rÃ©solution/proximitÃ©, ordre des dessins qui se recouvrent et bornes inconnues, exceptions imbriquÃ©es de texte. Les tests Forge couvrent la rÃ©duction d'un mesh connectÃ©, conservation de sa surface/bordure/UV, erreurs et discontinuitÃ©s UV. Le test OpenGL logiciel compare les pixels ordinaires/regroupÃ©s avec huit matrices, couleurs, lumiÃ¨res et overlays diffÃ©rents, puis des dessins ordinaires aprÃ¨s restauration.

La compilation Forge et les rÃ©sultats exacts du candidat seront consignÃ©s aprÃ¨s GitHub Actions. Les tests de source/bytecode et le rendu logiciel ne remplacent pas une partie avec Oculus, le shader choisi et MTR France Addon. Aucun monde privÃ© n'est demandÃ©.

Les mesures utilisateur disponibles concernent le prÃ©cÃ©dent JAR db74325 : 45â€“50 FPS avec train en gare et shaders, 60â€“70 sans train ; gÃ©nÃ©ralement 90â€“100 sans shaders. Les captures Spark ne permettent pas d'annoncer le FPS de ce candidat ou sa victoire sur V2. Le coÃ»t du profiler a perturbÃ© le dernier test : comparer les versions sans profiler dans la mÃªme scÃ¨ne, puis capturer uniquement si nÃ©cessaire.

## Interrupteurs de comparaison

`-Dmtr.next.textBatch=false`, `-Dmtr.next.instances=false`, `-Dmtr.next.oculusInstances=false`, `-Dmtr.next.grouping=false`, `-Dmtr.next.regions=false`, `-Dmtr.next.visibility=false`, `-Dmtr.next.lod=false`. Ils permettent d'isoler la cause d'un dÃ©faut ou d'une rÃ©gression. Les appels complets du shader/Framebuffer de V3 ne sont pas supprimÃ©s.

Source primaire du contrat d'attributs Oculus : [VanillaTransformer Ã  la rÃ©vision inspectÃ©e](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/VanillaTransformer.java). Les cibles MTR/Minecraft-Mappings sont vÃ©rifiÃ©es contre le bytecode embarquÃ©, pas seulement contre l'upstream actuel.
