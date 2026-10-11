# Suite mesurée après le test shaders

Le Spark m156dT84Yo contient le thread de rendu, sur une minute, avec le sampler Java à 4 ms et tous les threads. Le profilage perturbe les FPS selon le retour utilisateur ; les pourcentages suivants servent à localiser des chemins, pas à annoncer des gains par image. Les captures V4 antérieures ne sont pas des scènes synchronisées.

## Corrections de cette candidate

- Détection des ombres : 9,48 % inclusifs du temps de rendu échantillonné, dont 5,45 % sous la sélection des LOD. Le helper des mappings reconstruit une stack trace à chaque question. Une injection client lit maintenant la méthode publique Oculus 1.8.0 ShadowRenderingState.areShadowsCurrentlyBeingRendered, dont le bytecode lit directement ShadowRenderer.ACTIVE. La résolution optionnelle a lieu une fois ; la valeur est relue à chaque question, sans cache par image ni état périmé entre main/shadow. Oculus absent, version incompatible, échec du lecteur ou option JVM -Dmtr.next.shadowState=false : détecteur historique conservé. Aucun changement de l'application des shaders/uniformes.
- LOD : retour direct pour les objets proches, familles non train, option désactivée et modèles sans LOD disponible avant toute question d'ombres. Un seul choix de passe ensuite, au lieu de deux.
- Entretien du cache : 3,86 % dans CachedResource.tick, dont 3,49 % d'itération WeakHashMap.hasNext. Remplacement du balayage par une file d'échéances ordonnée et des références faibles. Seules les ressources chargées y entrent ; les renouvellements de TTL sont reschedulés lorsque l'ancienne échéance arrive. Références collectées retirées via ReferenceQueue. Token global de chargement, forçage, égalité de l'échéance et libération des anciennes générations préservés. Le travail des expirations réellement dues reste présent ; aucune borne en millisecondes promise.

Les tests du vrai lecteur optionnel couvrent les bascules main/shadow dans la même image, son absence, son échec et sa désactivation. Les tests du vrai registre/cache couvrent échéances identiques et renouvelées, références collectées simulées, 50 000 échéances futures sans callback, 20 000 caches non chargés sans inscription, TTL et token global. Les stubs n'établissent pas à eux seuls l'application réelle du nouveau mixin.

## Autres chantiers concrets

| Priorité | Travail possible | Preuve / limite |
| --- | --- | --- |
| 1 | Couvrir davantage de modèles identiques par l'instancing, en quantifiant les refus par VAO/lumière/matériau et la longueur des groupes | glDrawElementsInstanced est réellement observé sous Oculus, mais la majorité des dessins restent ordinaires. Aucun tri opaque arbitraire avant contrôle des overlays, profondeur et uniformes d'objet. |
| 2 | Mettre en cache les données de rendu des panneaux PIDS et regrouper les dessins de texte compatibles | TextWrapper.drawText JCM : 6,61 % inclusifs. Distinguer contenu variable, scrolling, lumière, transformations et rendu du texte ; identifier le JAR/source exact avant injection addon. |
| 3 | Séparer rendu visuel et interaction/audio pour rejeter davantage de voitures proches hors champ | Le bypass conservateur actuel conserve le traitement à proximité. Le retirer globalement casserait potentiellement sons/embarquement ; isoler ces fonctions avant réduction du travail visuel. |
| 4 | Réduire préparation des callbacks, matrices, connexions et conditions inchangées des trains | MainRenderer reste à 44,49 % inclusifs ; drawAll à 20,64 %. La différence contient plusieurs fonctions et ne mesure pas à elle seule une cible isolée. Un cache doit invalider portes, bogies, animation, modèle et lumière. |
| 5 | Partager géométrie et commandes des régions, réduire les doublons de modèles CPU/GPU | Les régions fonctionnent mais sélection/hash/queues restent présents ; budget mémoire et durée de résidence à vérifier. Une arène globale est une refonte, pas une optimisation gratuite. |
| 6 | LOD plus riche et ombres adaptées aux modèles complexes | Le LOD actuel retire seulement de petits composants isolés. Simplification de surfaces, alpha/glow et silhouettes demandent une validation propre ; aucun Hi-Z/GPU culling n'est déjà livré. |

Le batching, les données par instance et le multi-draw sont des mécanismes OpenGL distincts ; [documentation Khronos sur le rendu](https://wikis.khronos.org/opengl/Vertex_Rendering) et [les attributs par instance](https://wikis.khronos.org/opengl/Vertex_Specification). Leur potentiel sur ce mod dépend du coût réellement évité et de la compatibilité Oculus/addons.

## Audit complémentaire des panneaux et des passes

Le mapping de classes du Spark attribue TextWrapper à **jsblock 2.1.2+1.20.1** (Joban Client Mod), et non à Métropolis. La [source JCM v2.1.2](https://github.com/DistrictOfJoban/Joban-Client-Mod/blob/v2.1.2/fabric/src/main/java/com/lx862/jcm/mod/scripting/jcm/pids/TextWrapper.java) utilise précisément le package observé. Elle reconstruit les textes formatés, mesure les largeurs et prépare les lignes dans le callback de rendu ; le marquee mesure les caractères puis dessine chaque caractère visible individuellement. Les deux niveaux possibles sont donc un cache borné de layout/glyphes invalide lors des changements texte/font/style/resources, puis des quads regroupés ou une surface de panneau conservée. Le déplacement du scrolling doit rester animé ; les ETA/contenus ne doivent pas être figés. Les 6,61 % incluent le dessin, donc un cache de strings seul ne retire pas tout ce coût. Aucune injection JCM n'est incluse dans ce JAR.

[PIDSRenderer v2.1.2](https://github.com/DistrictOfJoban/Joban-Client-Mod/blob/v2.1.2/fabric/src/main/java/com/lx862/jcm/mod/render/block/PIDSRenderer.java) recherche les plateformes proches lorsque l'affectation est vide, construit leurs listes puis demande les arrivées et prépare les transformations à chaque rendu. Une invalidation par position/configuration/station pourrait éviter de refaire la recherche ; les réponses d'arrivées restent dynamiques. Le poids de cette recherche précise n'est pas encore isolé dans le Spark.

Dans RenderVehicles, bogies, recherche des portes ouvertes, planchers, embarquement, connexions et rendu se trouvent dans le même traitement de voiture. Les ombres réutilisent déjà les poses mais répètent encore plusieurs préparations d'interaction et de callbacks. Le chantier consiste à conserver les interactions sur la passe principale et partager les données utiles, puis filtrer seulement les soumissions visuelles. Un simple rejet de la voiture proche supprimerait aussi une partie de ce travail nécessaire. Ces refontes restent à implémenter et valider.

## Ce que le profil ne permet pas encore de choisir

- Généraliser une géométrie pilotée par GPU (Hi-Z, compute et multi-draw indirect) demanderait le coût GPU et les nombres d'objets/triangles évitables. Spark observe les piles CPU et les attentes du pilote ; le poids d'un appel GL n'est pas une mesure du temps de ses triangles sur le GPU.
- Un vrai LOD de surfaces pourrait apporter plus que la suppression des petits composants, surtout pour les ombres, mais doit préserver seams UV, normales, matériaux, transparence et silhouette. Il faut produire des niveaux par modèle et tester les modèles addon ; les réductions sont dépendantes du contenu.
- Mettre tout le train dans une unique instance ne suffit pas lorsque portes, bogies, animations et conditions de matériau sont différents. Le partage doit conserver ces groupes animés et leurs états par objet. La fusion des maillages, l'instancing de copies et le cache de préparation sont complémentaires.

## Validation et artefact

Code compilé : `62db10751f9098daa5d3595ac7ade21b06e4445f`. [GitHub Actions 38157947923](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38157947923) réussi : huit groupes de checks CPU, quinze tests de géométrie (zéro échec/ignoré), comparaison de pixels OpenGL logiciel, contrats statiques des mappings et vérification du JAR. Les logs complets des deux jobs ont été inspectés. Aucun échec ; deux avertissements Javadoc hérités, un avertissement de dépréciation ThreadDeath dans le harness Java 21 et dépréciations des outils CI restent présents.

[Artefact Forge](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38157947923/artifacts/11709577605) récupéré : 84 426 877 octets ; SHA256 `c4d999713fa5b0e981d51d3a6194484cb7f66824c73dd6566b27a48a6fc05265`. ZIP SHA256 `c6f6be66569c12d138609fba2f8b96ad2621a64f0f8783d83aeceb6109e799dd`. Rapports récupérés : quinze tests réussis ; ZIP SHA256 `db2c4caf8573cfd7449eeb5e238a36ea9c1f688b0b10baf730bfebaf6de01021`. Copie locale : `artifacts/MTR-forge-4.0.5+1.20.1-next-hotspots-62db107.jar`. Dix-neuf mixins clients enregistrés et présents. Les commits de documentation suivants ne changent pas ce binaire.

L'injection du nouveau lecteur d'ombres reste à valider dans Minecraft/Oculus. Aucun FPS, résultat visuel ni gain en jeu n'est attribué à ce JAR avant essai. Version interne 4.0.5 conservée ; la candidate db74325 déjà testée reste disponible. Références V2/V3/V4 revérifiées inchangées.
