> Suite implémentée : [six chantiers FPS, code et limites actuels](mtr-six-fixes-fr.md). Les restrictions décrites ci-dessous sont celles du code audité ou des versions historiques ; elles ne remplacent pas le suivi du nouveau candidat.

# Audit FPS du dépôt : ce qui bloque encore réellement

Audit du 11 octobre 2026, sources `d6ae448394a6e36ab31204b059521cbe6a55d463`.
Profil principal : `m156dT84Yo.sparkprofile`, thread **Render thread** exclusivement, 59 996 ms échantillonnées. Ce profil concerne la version architecture `db74325`, avant les corrections de `62db107`. Les coûts anciens du détecteur d'ombres et du balayage des ressources ne décrivent donc pas nécessairement le dernier JAR.

L'objectif est de réduire le travail réellement soumis au moteur, plutôt que d'accumuler des caches et des drapeaux dont la couverture reste inconnue. Les constats ci-dessous décrivent du code ou des piles observées. Les gains FPS restent à mesurer en jeu.

## Périmètre et limites

- Arbre Git complet inventorié : 5 333 entrées, réponse non tronquée. Ce nombre inclut les répertoires et les assets.
- Analyse statique des 422 fichiers Java communs, 39 367 lignes : points d'entrée de rendu, ticks, simulation, allocations, parcours et accès aux ressources. Lectures détaillées ciblées sur les chemins actifs du profil et leurs dépendances ; cela ne signifie pas une revue manuelle ligne par ligne des 422 fichiers.
- 69 fichiers du paquet `render`, 45 de `resource`, 15 de `client`, 19 mixins ; extension de la recherche aux 107 fichiers de blocs, 14 de données, 37 de paquets, 12 de sons, entrées client et hooks Forge.
- Les outils de construction, le site web, les écrans de configuration et les servlets ne sont pas exécutés comme rendu de la gare. Ils ont été identifiés dans l'inventaire, sans leur attribuer artificiellement un problème de FPS permanent.
- Le checkout est partiel et ne contient pas les assets sources. Les 43 fichiers Blockbench du JAR livré ont été examinés séparément. Ce sont les modèles intégrés, pas une identification du train France Addon utilisé dans le monde.
- Vérification supplémentaire dans les classes réellement embarquées : `GraphicsHolder.drawText`, `BatchManager.RenderCall.draw`, `VertexAttributeState.apply`. Le clone Minecraft-Mappings sert à lire le code ; le bytecode du JAR fait foi quand les versions diffèrent.
- Le premier fichier `9JerVeiQ2B` contient le serveur seul. Il ne mesure pas le rendu client. Le dernier capture tous les threads et l'utilisateur a signalé un fort ralentissement pendant le profilage.

## Ce que les piles permettent vraiment de dire

Les pourcentages sont des fractions du temps échantillonné du thread de rendu. Les lignes inclusives se chevauchent et ne doivent pas être additionnées. Un appel OpenGL peut contenir une attente du pilote ; ce n'est pas un chronométrage GPU.

| Chemin | ms échantillonnées | Part | Interprétation |
|---|---:|---:|---|
| `MainRenderer.render` | 26 692 | 44,49 % | Ensemble du rendu MTR, incluant les callbacks d'addons |
| `BatchManager.drawAll` | 12 380 | 20,64 % | Soumission des modèles optimisés |
| `RenderCall.draw` individuel | 9 384 | 15,64 % | Le chemin individuel reste très présent |
| `VertexAttributeState.apply` | 6 440 | 10,73 % | Attributs et réapplication du shader, tous appels de cette méthode |
| `ExtendedShader.apply` sous les appels individuels MTR | 4 452 | 7,42 % | Coût précisément attribuable aux modèles MTR individuels |
| `CustomUniforms.push` sous ces mêmes appels | 2 080 | 3,47 % | Inclus dans la ligne précédente |
| JCM `TextWrapper.drawText` | 3 968 | 6,61 % | Formatage et surtout chaîne effective de dessin du texte |
| `GraphicsHolder.drawText` : `ExtendedShader.apply` descendant | 1 456 | 2,43 % | Réapplication de shaders déclenchée par le texte immédiat |
| `GraphicsHolder.drawText` : `glBindFramebuffer` descendant | 1 636 | 2,73 % | Vidages et nettoyage de rendu associés au texte |
| `RenderVehicles.render` | 2 816 | 4,69 % | Préparation des wagons ; le dessin différé est compté ailleurs |
| `RenderRails.render` | 2 412 | 4,02 % | Sélection et préparation des rails, malgré les caches |
| `StaticRegionRenderer.flush` | 2 268 | 3,78 % | Préparation du cache et des plages, pas uniquement reconstruction GPU |
| `PredictiveVehiclePreloader.consider` | 1 200 | 2,00 % | Une partie vient de l'ancien détecteur d'ombres, déjà corrigé |

Le chiffre global `glBindFramebuffer = 7,32 %` ne signifie **pas** que les wagons coûtent 7,32 % en changement de framebuffer : 0,47 % est sous les appels individuels MTR, 0,013 % sous les groupes instanciés, 2,73 % sous le texte Mapping, et 4,11 % ailleurs. Cette attribution change les priorités.

## 1. Texte : supprimer les vidages par appel avant de construire un cache d'écrans

**Priorité immédiate, preuve forte.** Les trois surcharges de `GraphicsHolder.drawText` créent en rendu monde un `MultiBufferSource.immediate` autour du buffer du Tesselator, dessinent le texte puis appellent `endBatch`. Le bytecode du JAR confirme ce comportement. En GUI, elles appellent `drawContext.flush` ; ce chemin doit rester séparé.

Dans [JCM v2.1.2 TextWrapper](https://github.com/DistrictOfJoban/Joban-Client-Mod/blob/v2.1.2/fabric/src/main/java/com/lx862/jcm/mod/scripting/jcm/pids/TextWrapper.java), le défilement appelle cette méthode pour chaque caractère visible. La pile du profil montre ensuite `ImmediatelyFast.BatchableBufferSource`, `BufferUploader`, `VertexBuffer`, Oculus `ExtendedShader.apply`, puis les appels de dessin. La présence d'ImmediatelyFast ne supprime donc pas ce chemin coûteux dans la capture fournie.

**Changement à construire :** un buffer de texte partagé dans un périmètre de dessin monde connu, avec vidage aux vraies frontières de matériau/état et à la fin du périmètre. Commencer par les textes successifs d'un même callback, notamment le défilement JCM. Élargir au panneau seulement après vérification de l'ordre texte/images/profondeur. Utiliser un buffer détenu par ce mécanisme ; laisser un buffer du Tesselator ouvert pendant que d'autres callbacks l'utilisent serait incorrect.

**Ce que cela attaque :** beaucoup d'envois GPU et de réapplications du shader pour peu de glyphes. Le nombre de dessins doit suivre les segments compatibles de police/état, au lieu du nombre de caractères. Exemple théorique : 30 caractères de même état peuvent former un segment ; ce n'est pas une mesure du panneau de l'utilisateur.

**Suite possible :** géométrie de glyphes conservée, largeurs et styles calculés seulement quand le contenu/la police changent ; déplacement du défilement par transformation. Les textures de panneaux ne viennent qu'après, avec invalidation sur ETA, langue, scripts et reload. Ce coût de 6,61 % n'est pas intégralement supprimable.

## 2. Instancing : débloquer la formation des groupes

**Priorité élevée, preuve du frein forte, couverture effective encore inconnue.** Voir [InstancedModelRenderer.java](../fabric/src/main/java/org/mtr/mod/render/InstancedModelRenderer.java), lignes 71–108, et [ConsecutiveRuns.java](../fabric/src/main/java/org/mtr/mod/render/ConsecutiveRuns.java).

Le code ne groupe que les appels **consécutifs**, de même identité `VertexArray`, famille, couleur, lumière et overlay. Il exige au moins quatre appels. Sous Oculus, les groupes sont limités à huit. Un ordre `A B A B A B A B` produit des groupes unitaires, même si quatre occurrences de A et quatre de B sont compatibles. Deux bogies compatibles seuls restent également sous le seuil. Des lumières de wagons différentes coupent le groupe.

**Changements :**

1. Construire une liste de commandes sélectionnées une seule fois par passe : VAO/LOD, éligibilité, état et distance. Le getter `mtr$array()` refait aujourd'hui une sélection LOD dans les comparaisons puis dans les trois accès du dessin individuel ; le dernier correctif en rend une partie moins chère, mais n'élimine pas la répétition structurelle.
2. Former des groupes par clé dans les segments opaques dont le réordonnancement est prouvé sûr. Le simple fait d'écrire la profondeur ne rend pas tous les réordonnancements sûrs : surfaces coplanaires, stencil, shaders à images et dépendances d'ordre doivent conserver leur ordre.
3. Mettre la lumière et la couleur dans les données par instance, au lieu d'exiger leur égalité. Sous Oculus, la traduction de ces attributs doit respecter les entrées réellement générées par le shader. Cela demande une extension du chemin de shader, pas la suppression de `apply`.
4. Canoniser les géométries immuables identiques quand elles ont été chargées dans plusieurs wrappers. Identité différente ne signifie pas géométrie différente, mais la comparaison doit inclure UV, matériau, variante, mapping et génération.
5. Compter les refus par cause : ordre, lumière, taille de groupe, matériau, shader non supporté, buffer occupé. Les métriques actuelles ne fournissent pas ce dénominateur et le rapport d'instances concerne surtout le backend sans shaders.

Le profil prouve que l'instancing Oculus s'exécute sur certains groupes. Les 60 ms sous `glDrawElementsInstanced` ne donnent ni le nombre d'instances ni leur couverture. Il est incorrect de les transformer en « 0,1 % des wagons instanciés ».

## 3. Régions : couvrir les parties compatibles des modèles et paginer

**Priorité élevée, restrictions certaines, fréquence des replis à instrumenter.** Voir [RailGeometrySnapshots.java](../fabric/src/main/java/org/mtr/mod/render/RailGeometrySnapshots.java), [RailBatchGeometry.java](../fabric/src/main/java/org/mtr/mod/render/RailBatchGeometry.java), [StaticRegionRenderer.java](../fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java) et [PersistentRegionCache.java](../fabric/src/main/java/org/mtr/mod/render/PersistentRegionCache.java).

- `eligible(meshes)` rejette **tout le modèle** si un mesh est translucide, cutout, sans écriture de profondeur ou à lumière forcée. Une partie solide pourtant compatible perd donc le bénéfice du cache si le modèle contient aussi une vitre ou un élément lumineux.
- Aucun snapshot pour une source au-delà de 16 000 sommets. Maximum global : 200 000 sommets conservés côté CPU.
- Une entrée de région n'est compilée que si son contenu total reste sous 16 000 sommets. Plusieurs petites ressources dépassant ce total peuvent rendre une région non compilable.
- Le cache actuel dispose de 64 entrées, 256 000 sommets et un build par `flush`. Les entrées vues dans la passe sont protégées ; lorsque le budget ne permet plus une entrée, le repli dessine individuellement.
- La lumière fait partie de `Selection`. Un changement peut entraîner une nouvelle compilation de contenu, même si la géométrie n'a pas changé.

**Changements :** séparer les meshes compatibles et leur reste rendu normalement, sans doublon ; plusieurs pages bornées par région ; registres de contenu avec révisions de modèle/transform/lumière ; données de lumière séparées lorsque le format le permet ; budgets explicites en octets et statistiques de repli/résidence. Augmenter tous les plafonds sans traiter ces causes peut seulement déplacer la limite vers la mémoire et les uploads.

## 4. Rails : conserver aussi le travail de préparation, pas seulement les sommets

**Priorité élevée côté CPU, preuve forte.** [RenderRails.java](../fabric/src/main/java/org/mtr/mod/render/RenderRails.java), lignes 272–303, parcourt encore les segments et leurs styles, lit deux niveaux de lumière, calcule `atan2`, racine, rotations, crée transformations et callback. [RailSectorBatcher.java](../fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java) construit ensuite une matrice et une clé de localisation. `StaticRegionRenderer.queue` recopie une matrice et fabrique une clé de 16 floats. Son `flush` refabrique des listes et tables de plages visibles.

**Changement :** registre persistant de segments avec poses/styles précompilés, et sélection de handles/plages à partir de la visibilité. Ne recalculer que lorsqu'un rail, style, ressource, éclairage ou monde change. Le cache de `RailMath` et l'index spatial existent déjà ; ajouter un autre cache de points ne règle pas ce chemin. Les plages adjacentes peuvent ensuite être fusionnées avant `glMultiDrawElements`.

La stabilité de la lumière doit venir d'une invalidation correcte des sections, pas d'un gel arbitraire qui laisserait les torches et le jour/nuit incorrects. Les outils d'édition doivent continuer à produire leur aperçu.

## 5. Trains : rendre le dessin indépendant des interactions

**Potentiel élevé en gare, changement architectural.** [RenderVehicles.java](../fabric/src/main/java/org/mtr/mod/render/RenderVehicles.java), lignes 116–212 et 339–348, mélange rendu, portes, passagers, sols, montage et déplacement du joueur. Les poses ont déjà un cache par frame ; il serait trompeur de proposer leur mise en cache comme une nouveauté.

[TrainVisibility.java](../fabric/src/main/java/org/mtr/mod/render/TrainVisibility.java) utilise des limites très conservatrices : rayon `max(16, longestDimension*2+8)`, et le frustum ne rejette pas les wagons dans la zone de proximité `rayon+48`. Cela protège les interactions/audio ; le test d'occlusion existant reste toutefois actif. Le train du joueur bénéficie également d'une exemption.

**Changement :** état d'interaction préparé une fois avec ses dépendances propres, puis commandes visuelles sélectionnées par passe. Cela permet de continuer portes, sons, montage et mouvement tout en évitant de soumettre une voiture hors champ près du joueur. Construire les bounds depuis les meshes, bogies, portes animées et connexions ; conserver le repli si les bounds sont inconnus. Une visibilité du pass principal ne doit pas masquer un objet qui projette une ombre visible.

Après cette séparation, des sous-groupes intérieur/extérieur ou clusters peuvent supprimer des parties invisibles d'un wagon. **Ne pas cacher l'intérieur juste parce que le joueur est dehors** : les fenêtres et les portes ouvertes permettent de le voir. Une occlusion conservatrice est nécessaire.

## 6. Géométrie : le LOD actuel est trop limité pour alléger un gros train proche

**Potentiel GPU, absence de mesure GPU dans Spark.** [ModelDetailGeometry.java](../fabric/src/main/java/org/mtr/mod/render/ModelDetailGeometry.java) supprime seulement des composants déconnectés dont l'étendue est sous un seuil. Un grand composant connecté garde tous ses triangles. [ModelDetailCache.java](../fabric/src/main/java/org/mtr/mod/render/ModelDetailCache.java) active cette sélection à 160 blocs en rendu principal et 64 en ombres, uniquement pour la famille TRAIN avec distance connue. Cela ne simplifie donc pas le wagon proche qui entre dans la gare.

Dans [ModelPropertiesPart.java](../fabric/src/main/java/org/mtr/mod/resource/ModelPropertiesPart.java), ligne 360, les portes animées sont soumises via la surcharge de queue à famille OTHER, sans distance explicite ; les LOD qui exigent TRAIN ne couvrent pas ce chemin. Les détails masqués par clignotement ou plage d'animation sont déplacés à `Integer.MAX_VALUE` puis peuvent encore être soumis : tester l'invisibilité avant d'envoyer les commandes est plus propre. Ce dernier point est une correction limitée, pas une promesse de gain massif.

**Changements majeurs :** LOD par erreur projetée en pixels ; simplification qui conserve coutures UV, matériaux, silhouettes et frontières d'animation ; variantes d'ombres adaptées à leur résolution ; clusters de géométrie avec bounds pour sélectionner des sous-ensembles. La [documentation primaire de meshoptimizer](https://github.com/zeux/meshoptimizer#simplification) décrit simplification tenant compte des attributs et choix selon erreur écran. Le portage MTR reste à réaliser et à valider, notamment sur les modèles Blockbench à normales plates.

Les 43 Blockbench intégrés incluent notamment un modèle S7 de 630 éléments/3 780 faces déclarées, soit 7 560 triangles **si toutes ces faces quad sont émises**. Ce comptage de fichier n'est pas le nombre effectivement dessiné : conditions, répétitions, portes et conversion changent le total. Il ne représente pas le train de l'utilisateur.

## Carte complémentaire : pistes concrètes, moins prioritaires ou conditionnelles

| Piste | Code / constat | Classement actuel |
|---|---|---|
| Géométrie de panneaux conservée | MTR `RenderPIDS` reconstruit lignes/pages ; JCM refait style et largeurs | Après suppression des flush ; utile pour le contenu stable |
| Arrivées indexées par plateforme | `ArrivalsCache.requestArrivals` refiltre tout le cache à chaque panneau | Coût observé 20 ms/60 s ; faible priorité dans cette capture |
| EyeCandy : distance et bounds de modèle | `RenderEyeCandy` retourne true pour distance et hors bounds | Potentiel scènes riches ; aucun coût identifié ici, ne pas imposer les bounds du bloc aux grands modèles |
| Portes PSD/APG, enseignes et horloges en commandes conservées | Rendu `ModelPart`/quads répété dans les callbacks | À couvrir par cache de parties fixes + petite partie animée ; 0,31 % pour le renderer de portes dans la capture |
| Ascenseurs : snapshot d'interaction et dessin | `RenderLifts` reproduit le mélange et calcule pose/path | 0,19 % ici ; faible priorité pour cette gare |
| Préchargement par changement de besoin | `PredictiveVehiclePreloader.consider` reparcourt les voitures proches et appelle `prepareModels` à chaque frame | Dédupliquer par génération et signature de rame ; soustraire l'ancien coût du détecteur avant estimation |
| Chargement GPU réellement progressif | `CachedResource` limite les opérations, pas le coût d'une opération ; `ModelPreparation.finish` conserve atlas/transform/upload principal | Utile aux pics d'arrivée/reload ; le profil ne prouve pas un gros coût permanent de compilation |
| Durée de résidence adaptée des véhicules | `VehicleModel.MODEL_LIFESPAN = 60000`, caches imbriqués dans `VehicleResource` | Retenir les modèles attendus dans un budget mémoire ; pas un preload infini de tout le pack |
| Accès au cache véhicule une fois par voiture | `queueBogie`, préparation des portes et `queue` redemandent le cache, avec quatre sous-maps | Réduction secondaire d'allocations et de recherches ; pas le gros poste permanent démontré |
| Réutiliser les identifiants/poses de rails | `RenderRails` et `CustomResourceTools` | À absorber dans le registre persistant, pas à présenter comme architecture majeure autonome |
| Occlusion à budget et snapshots sûrs | Worker serialise véhicules/lifts/rails et utilise des données de monde ; cache d'opacité existe déjà | Pour scènes gigantesques ; mesurer latence et CPU actif, ne pas déduire une charge des durées wall des threads |
| Réponses réseau incrémentales | `PacketUpdateVehiclesLifts` recrée les véhicules actualisés puis relit les paths et sync | 0,32 % ici ; probablement sujet de pics/réseaux plus grands, pas priorité FPS shader permanente |
| Simulation client à cadence indépendante | `MainRenderer` simule les véhicules à chaque rendu principal | 0,69 % ici ; déplacement de cadence risqué pour l'interpolation et les annonces, pas le premier chantier |
| Arena GPU et multidraw indirect | Actuellement plusieurs VAO et états par matériau | Étape ultérieure après commandes/instances ; besoin de données par objet et format commun, pas substitution magique d'un appel GL |
| Hi-Z / occlusion GPU | Pas de pyramide de profondeur MTR ni intégration par passe Oculus existante | Expérimental ; mauvaise cible de profondeur ou latence peut faire disparaître trains et ombres |
| Suppression de faces internes / greedy meshing | Modèles cubes avec UV et transparence variées | Ne supprimer que faces prouvées couvertes ; bénéfice inconnu sur le modèle utilisé |
| Imposteurs lointains | Représentation alternative de très petits trains/décors | Faible intérêt pour le gros train au quai ; nécessite variantes de vue/ombres |
| Overdraw et matériaux de fenêtres | `RenderStage`, `OpaqueMeshCombiner`, meshes translucides | Potentiel GPU en gare, à mesurer ; garder l'ordre et l'apparence des vitres |

Le [multidraw indirect Khronos](https://wikis.khronos.org/opengl/GLAPI/glMultiDrawElementsIndirect) reste une technique pertinente pour soumettre des commandes, mais ne remplace ni la préparation des données d'objet ni la gestion des shaders Oculus. Un backend avec capacités vérifiées et repli est nécessaire.

## Ce qui a déjà été fait et ne constitue plus une découverte

Index spatial des rails/stations, cache des points de rails, fusion de meshes opaques par matériau, réordonnancement triangles/sommets, cache d'attributs sans shaders, limitation de certains uploads, worker de parsing, cache de poses, instancing partiel, régions partielles, suppression de petits composants distants. Le dernier JAR corrige également le détecteur d'ombres par pile et le balayage global des caches chargés. Ajouter « cache », « threads » ou « culling » à une liste ne décrit pas le travail restant.

Le profil révèle aussi le prix de mécanismes ajoutés dans notre branche. L'audit doit évaluer leur bénéfice net : couverture, temps de préparation, mémoire et replis. L'existence d'une optimisation dans le code ne prouve pas qu'elle aide dans cette scène.

## Ordre de chantier recommandé

1. Batching de texte dans un périmètre sûr : preuve directe de vidages inutiles et cible shader importante.
2. Liste de commandes résolues, compteurs de couverture, regroupement opaque prouvé sûr et lumière par instance : attaquer les 7,42 % de réapplication Oculus sous les appels MTR individuels.
3. Partition des modèles statiques, pages de régions et registre persistant des rails : couvrir ce qui tombe aujourd'hui en repli et retirer la préparation répétée.
4. Séparation interaction/rendu des trains, bounds exacts et sélection par passe : rendre une réduction du travail proche possible sans casser le jeu.
5. LOD avec erreur écran et clusters : alléger réellement la géométrie et les ombres, après identification des meshes coûteux.

Ces chantiers ont des interfaces communes, mais les effets doivent rester séparément désactivables. Validation : nombre de commandes/flush/instances/triangles, temps de préparation et GPU différés par passe, puis FPS et frametimes en jeu dans une scène comparable. Aucun compteur ne remplace la vérification de portes, boarding, sons, vitres, textes, shadows, reload et France Addon.

**Résultat de ce tour : audit et priorités, aucun nouveau correctif runtime ni nouveau JAR.** Le JAR `62db107` reste le dernier construit. La V2 reste intacte comme référence. Aucun gain FPS supplémentaire n'est revendiqué.
