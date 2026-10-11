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

## Validation et artefact

Tests locaux du nouveau registre/lecteur réussis. Compilation GitHub Actions, vérification du JAR et récupération de l'artefact en cours. Les références V2/V3/V4 restent intactes. Aucun FPS de cette correction n'est encore mesuré ; les 45–50 FPS avec train/shaders et 90–100 souvent sans shaders concernent la candidate précédente testée par l'utilisateur.
