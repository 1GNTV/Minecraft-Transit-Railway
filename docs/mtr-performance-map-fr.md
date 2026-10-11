# Cartographie des optimisations majeures — MTR Forge 1.20.1

Date : 11 octobre 2026 (Europe/Paris). Sources de travail : `work/mtr-v4-optimization`, tête inspectée `0530db54ed3c81b96a61555afdff15057007ad77`, code compilé `b0c19aae`. V2 de référence : `dadf9c9` ; V4 initiale : `2b048396`. Ce document est une carte de travail, pas une annonce de gains ni une implémentation des 40 pistes.

## Ce que la carte couvre

Les grandes familles de changements plausibles pour le mod, sa dépendance Minecraft-Mappings, l'intégration Oculus, les ressources/addons et, séparément, le core client/serveur. Aucun inventaire statique ne peut garantir chaque optimisation possible de chaque mod du pack : les points non audités ou non mesurés sont explicitement signalés.

**40 pistes architecturales ou conditionnelles + 8 fondations.** Les pistes ne sont pas indépendantes : plusieurs visent le même travail et leurs gains ne s'additionnent pas. « Fort » qualifie le potentiel dans une scène adaptée, jamais un pourcentage de FPS garanti. Les pistes de chargement visent souvent les pics/p99, pas le FPS moyen. Les pistes serveur visent les ms/tick ; en multijoueur elles n'améliorent pas directement le rendu du client.

## Preuves et correction de la précédente liste

- Profils Spark fournis : deux captures de 60 s du render thread, avec chemins shader différents ; aucune capture V2 identifiée par SHA pour un A/B.
- BatchManager.drawAll : 27,967 % / 34,827 % inclusifs ; VertexAttributeState.apply : 22,013 % / 29,460 % ; glDrawElements via MTR VertexArray : 4,427 % / 3,433 %. Ces postes se chevauchent.
- Le shader profile contient Oculus/Iris ExtendedShader.apply : 28,220 % inclusifs tous appelants, ce qui n'est pas 28,220 % attribuable exclusivement à MTR. Spark ne distingue pas calcul CPU, attente native et durée GPU.
- **Culling, cache de modèles, regroupement par PartCondition, préchargement optionnel et threads de préparation existent déjà.** Les propositions ci-dessous cherchent à les faire évoluer. « Ajouter du culling/des caches/un thread » ne serait pas une découverte.
- Le JAR Work actuel restaure des optimisations V2 omises de V4, sécurise le cache d'attributs shaderless et fusionne une partie des OBJ opaques. Il ne contient ni renderer par secteur, ni instanciation nouvelle, ni backend GPU indirect.
- Les mappings upstream actuels diffèrent du JAR livré : les cibles critiques précédentes ont été inspectées dans le binaire exact. Toute refonte doit partir de cette API, pas supposer un remplacement compatible par la dernière version.

## Ordre de chantier recommandé

1. **S1–S3 : établir V2/Work et compter la contribution des modèles réels.** Mesurer shader.apply et dessins par famille avant de choisir un backend.
2. **01, 03, 04 : supprimer les soumissions et dessins évitables**, rails persistants puis groupes de trains réellement consolidés. Objectif : visualisation identique.
3. **11, 16, 17 : réutiliser la pose entre passes et filtrer plus tôt.** Mesurer leur coût propre, aujourd'hui moins dominant que l'application des états.
4. **26–29 et 33 : supprimer les pics** si cache misses, compilation ou uploads correspondent effectivement aux premières arrivées.
5. **05–07 et 12 : instanciation/multi-draw et transaction shader adaptée**, seulement après que le format de données permette des lots corrects avec Oculus.
6. **08, 20 : prototypes GPU avancés**, uniquement si CPU/driver restent limitants après les étapes précédentes.
7. **15, 21, 22 : options de visibilité/qualité**, lorsque acceptées et validées. **38–40 : campagne réseau/serveur séparée**, si profils correspondants.

Il faut pouvoir désactiver chaque changement indépendamment. Conserver un chemin de rendu compatible ; ne pas utiliser le frustum caméra pour supprimer des ombres pertinentes. V2 reste une référence indépendante.

## Inventaire détaillé

Chaque fiche donne le mécanisme, le niveau de preuve et le test qui déciderait de son adoption.

### Soumissions et GPU

**01. Rails persistants par secteur — Fort candidat**

- **Point de départ :** RenderRails.java:272–317 recrée transformations, lectures de lumière et soumissions par répétition.
- **Modification :** Construire des maillages fixes par secteur et matériau ; ne reconstruire que les secteurs modifiés.
- **Contraintes :** Préserver styles addon, normales, précision par origine locale, éclairage, previews et changements de rails.
- **Décision par mesure :** Dessins, shader.apply, temps de soumission et GPU ; caméra fixe et édition de rails.

**02. Décors fixes regroupés par secteur — Conditionnel fort**

- **Point de départ :** RenderEyeCandy, ObjectResource et rendu des panneaux : audit détaillé et volumes encore nécessaires.
- **Modification :** Appliquer le stockage persistant aux éléments réellement statiques de la station.
- **Contraintes :** Séparer éléments animés, textes dynamiques, transparence et états de block entities ; gain dépend du nombre d'objets.
- **Décision par mesure :** Dessins par famille et temps de station avant/après ; vérifier changements de blocs.

**03. Fusion complète des groupes de train compatibles — Fort candidat**

- **Point de départ :** VehicleResource.java:530–547 compile déjà par PartCondition ; fusion OBJ partielle dans le JAR actuel.
- **Modification :** Consolider aussi la géométrie compatible OBJ/Blockbench à l'intérieur d'une même condition, transformation et passe.
- **Contraintes :** Ne pas fusionner voitures, bogies, portes ou conditions indépendantes ; fermeture GPU avec ownership clair.
- **Décision par mesure :** Maillages/dessins par modèle réel France Addon ; portes et animations identiques.

**04. Batches réels de soumission — Fort candidat**

- **Point de départ :** Le BatchManager livré trie par matériau mais conserve un VertexArray.draw par maillage.
- **Modification :** Construire des lots réellement dessinables ensemble, avec état de matériau appliqué au niveau du lot.
- **Contraintes :** Des matrices/lumières différentes doivent être encodées par sommet/instance ou rester séparées ; tri translucide conservé.
- **Décision par mesure :** Dessins, changements d'état et applications de shader par lot, puis temps de passe.

**05. Instanciation des éléments répétés — Fort, intégration difficile**

- **Point de départ :** Rails répétés soumis individuellement ; voie d'instanciation à construire.
- **Modification :** Un maillage partagé et un buffer de positions, matrices, couleurs et lumière pour ses exemplaires.
- **Contraintes :** Adaptation des vertex formats et shader packs nécessaire avec Oculus ; fallback obligatoire.
- **Décision par mesure :** Instances par dessin, volume transféré, temps CPU/GPU ; test shaders et addon.

**06. Arènes de buffers GPU partagées — Fondation conditionnelle**

- **Point de départ :** Le rendu livré utilise des VertexArray par maillage ; organisation GPU à cartographier quantitativement.
- **Modification :** Stocker des plages de géométrie dans des buffers communs, avec offsets et allocation par génération.
- **Contraintes :** Allocation, fragmentation, déplacement et destruction sûrs ; un buffer commun seul ne réduit pas les dessins.
- **Décision par mesure :** Nombre de VAO/buffers, binds, mémoire/fragmentation et coût du driver.

**07. Multi-draw des lots compatibles — Fort si beaucoup de petits dessins**

- **Point de départ :** Les profils contiennent glDrawElements ; aucun compteur de nombre de dessins dans Spark.
- **Modification :** Soumettre plusieurs plages compatibles en une commande, directe ou indirecte selon le matériel.
- **Contraintes :** Même programme, état et format ; le multi-draw ne permet pas de changer arbitrairement les uniformes entre sous-dessins.
- **Décision par mesure :** Dessins API versus plages, temps driver ; nécessite 04/06 et données par objet correctes.

**08. Sélection et commandes pilotées par GPU — Expérimental**

- **Point de départ :** Piste d'architecture, pas un problème spécifique mesuré.
- **Modification :** Faire sélectionner/compacter les objets visibles et générer les commandes indirectes sur GPU.
- **Contraintes :** Capacités OpenGL/compute/SSBO vérifiées ; compatibilité Oculus, synchronisation et fallback ; peut coûter plus que le CPU.
- **Décision par mesure :** Temps total CPU+GPU et latence, pas uniquement disparition du travail Java.

**09. Streaming GPU sans attendre les buffers en cours — Conditionnel**

- **Point de départ :** Les transferts et attentes GPU ne sont pas isolés par les profils actuels.
- **Modification :** Pour les nouvelles données dynamiques : anneau de buffers, orphaning ou mapping persistant avec fences.
- **Contraintes :** Ne jamais réécrire une plage lue par GPU ; support matériel requis ; géométrie actuelle déjà persistante à ne pas réenvoyer.
- **Décision par mesure :** Octets/image et attentes driver ; uniquement si le streaming devient un coût réel.

**10. Regroupement des textures et matériaux — Conditionnel fort**

- **Point de départ :** Queues par Identifier et matériaux ; fragmentation exacte à compter.
- **Modification :** Atlas ou textures en tableaux pour réduire les ruptures de lots entre petits matériaux.
- **Contraintes :** Conserver UV, répétition, mipmaps, samplers, normal/specular maps et shader packs ; tableau nécessite adaptation shaders.
- **Décision par mesure :** Matériaux uniques, binds et lots ; contrôle visuel PBR et coutures.

### Shaders et ombres

**11. Une pose de train partagée entre passes — Fort candidat de calcul évitable**

- **Point de départ :** MainRenderer.render appelle RenderVehicles pour les passes ; RenderVehicles.java:61 refait positions et lissage.
- **Modification :** Créer un snapshot de pose par image pour le réutiliser en rendu principal et ombres.
- **Contraintes :** Ne pas réexécuter une animation ou avancer la simulation en passe ombre ; visibility lists séparées.
- **Décision par mesure :** Calculs de poses/image, temps main/shadow, portes et mouvements identiques.

**12. Application Oculus complète au niveau du lot — Fort, intégration difficile**

- **Point de départ :** VertexAttributeState.apply fait ShaderInstance.apply ; Oculus recalcule inverses, uniformes et bindings.
- **Modification :** Définir une transaction compatible avec l'état attendu par Oculus pour appliquer une fois par lot réellement compatible.
- **Contraintes :** Nécessite données par objet correctement encodées ; aucun simple saut de apply ; shader packs arbitraires peuvent imposer fallback.
- **Décision par mesure :** Nombre d'applications, CustomUniforms, framebuffer binds et matrices ; validation visuelle.

**13. Uniformes dérivés recalculés seulement si modifiés — Conditionnel, sensible**

- **Point de départ :** Bytecode Oculus inspecté : matrices dérivées recalculées dans apply.
- **Modification :** Propager des versions des entrées pour réutiliser inverse, projection et normale quand leurs entrées n'ont pas changé.
- **Contraintes :** Uniformes custom, caméra, programme et passes doivent invalider ; hook dépendant de la version Oculus ; pas un cache global aveugle.
- **Décision par mesure :** Calculs/chargements évités et temps de passe ; test de tous les états invalideurs.

**14. Visibilité dédiée aux objets qui projettent des ombres — Conditionnel fort**

- **Point de départ :** Passes ombres présentes ; coût GPU et listes de visibilité pas encore isolés.
- **Modification :** Sélectionner selon le volume lumineux et les récepteurs pertinents, indépendamment du frustum caméra.
- **Contraintes :** Un objet hors écran peut projeter une ombre visible ; ne jamais réutiliser aveuglément l'occlusion caméra.
- **Décision par mesure :** Objets/dessins/triangles et temps GPU ombre ; soleil mobile et train hors écran.

**15. Maillages simplifiés pour les ombres — Fort avec compromis visuel**

- **Point de départ :** Hypothèse pour trains très détaillés ; aucun nombre de triangles mesuré.
- **Modification :** Créer des proxies conservant la silhouette pour les passes ombres.
- **Contraintes :** Option de qualité : petites ombres/interstices changent ; ne promet pas un rendu strictement identique.
- **Décision par mesure :** Triangles et temps GPU ombre, comparaison d'images et seuil de distance.

### Visibilité et géométrie

**16. Hiérarchie de visibilité par région/train/voiture — Fort candidat**

- **Point de départ :** Culling déjà présent ; RenderVehicles teste chaque voiture et RenderRails chaque candidat.
- **Modification :** Tester d'abord des groupes larges, puis les enfants utiles ; subdiviser les très longues zones.
- **Contraintes :** Bornes conservatrices couvrant mouvements, portes, addon et caméra embarquée ; shader shadows séparés.
- **Décision par mesure :** Tests et candidats/image, temps culling et objets finalement dessinés.

**17. Élimination grossière avant les calculs coûteux — Fort candidat**

- **Point de départ :** RenderVehicles.java:60–107 prépare toutes les poses détaillées avant rayTracing/visibilité.
- **Modification :** Utiliser bornes/positions grossières pour filtrer les trains sans contribution visuelle avant la préparation graphique détaillée.
- **Contraintes :** Conserver simulation autoritaire, collisions, embarquement, train monté et sons ; ne pas supprimer les mises à jour utiles au gameplay.
- **Décision par mesure :** Poses préparées et temps de préparation ; caméra embarquée et interactions.

**18. Snapshots d'opacité par section — Conditionnel fort**

- **Point de départ :** WorkerThread lit le monde ; CachedCullingDataProvider se réinitialise par passe.
- **Modification :** Construire des bitsets d'opacité par section et invalider avec les changements du monde.
- **Contraintes :** Publication thread-safe, unload/dimension/reload ; état incertain = visible ; pas de monde mutable lu sans garanties.
- **Décision par mesure :** Lectures de blocs et coût des rays ; aucune disparition lors d'édition.

**19. Culling incrémental avec budget et fraîcheur — Conditionnel fort**

- **Point de départ :** Worker possède déjà latest-pending queues et texture worker séparé ; commentaire sur passes rails longues.
- **Modification :** Prioriser zones proches/modifiées, conserver résultats valides et borner le travail ancien.
- **Contraintes :** Ne pas refaire une queue déjà coalescée ; un ancien résultat invisible ne doit pas cacher un nouvel objet/caméra déplacée.
- **Décision par mesure :** Âge des résultats, backlog, temps worker et faux négatifs ; mouvement rapide.

**20. Occlusion GPU avec profondeur hiérarchique — Expérimental**

- **Point de départ :** Pas implémentée ; occlusion CPU existante.
- **Modification :** Tester des volumes contre une pyramide de profondeur pour éliminer des objets masqués.
- **Contraintes :** Accès correct au depth buffer Iris, transparence et ombres ; résultats asynchrones conservateurs ; pas de readback bloquant.
- **Décision par mesure :** Coût de pyramide/tests versus dessins économisés, frame time total.

**21. Visibilité par salles et ouvertures — Conditionnel, stations structurées**

- **Point de départ :** Piste pour grands intérieurs ; aucun graphe de salles existant vérifié.
- **Modification :** Limiter les zones visibles selon les ouvertures des salles/stations identifiées.
- **Contraintes :** Portes, fenêtres, vitres, géométrie libre et modifications du monde rendent l'approche difficile ; fallback conservateur.
- **Décision par mesure :** Objets éliminés dans stations fermées ; traversées et changements d'ouvertures.

**22. Niveaux de détail selon la taille à l'écran — Fort avec compromis visuel**

- **Point de départ :** Modèles de trains potentiellement complexes ; budgets de triangles encore inconnus.
- **Modification :** Préparer plusieurs détails pour carrosserie, bogies, intérieur et petits accessoires, avec hystérésis.
- **Contraintes :** Préserver ce qui est visible à travers les vitres ; pas de suppression automatique de l'intérieur proche ; option de qualité.
- **Décision par mesure :** Triangles/pixels/temps GPU et transitions ; extérieur/intérieur.

**23. Optimisation des maillages sans réduire le détail — Conditionnel fort**

- **Point de départ :** RawMesh.distinct existe déjà ; coût géométrique réel à mesurer.
- **Modification :** Réordonner indices pour cache de sommets/fetch, supprimer duplications exactes et faces démontrées inutiles.
- **Contraintes :** Déduplication par tous les attributs, coutures, normales, UV/PBR ; ordre translucide et double-face préservés.
- **Décision par mesure :** Sommets uniques, invocations shader, mémoire et temps GPU ; comparaison visuelle.

**24. Compilation des transformations et animations — Conditionnel**

- **Point de départ :** StoredMatrixTransformations stocke des callbacks ; ModelPropertiesPart anime/conditionne les pièces.
- **Modification :** Compiler transformations constantes et graphe d'animation ; calculer chaque transformation commune une seule fois.
- **Contraintes :** Ne pas figer portes, oscillation, bogies, affichages et rotations addon ; matrice normale correcte.
- **Décision par mesure :** Opérations matrices et allocations/image ; animations conformes.

### Chargement et mémoire

**25. Cache disque des modèles compilés — Fort sur chargement et pics, FPS moyen conditionnel**

- **Point de départ :** ModelResourceLoader/VehicleModel chargent les ressources ; caches RAM déjà présents.
- **Modification :** Stocker une représentation compilée, indexée par contenu, versions, paramètres et génération de ressources.
- **Contraintes :** Les extraModelsSupplier peuvent dépendre de voiture/formation/addon ; cache doit inclure ces dépendances.
- **Décision par mesure :** Temps de première apparition/reload et cache hit ; validation après changement de pack.

**26. Préchargement prédictif des trains approchants — Fort sur pics si chargement responsable**

- **Point de départ :** CustomResourceLoader a déjà shouldPreload ; cache modèle 60 s dans VehicleModel.
- **Modification :** Préparer les véhicules et variantes qui approchent, avec priorité et limite RAM/VRAM.
- **Contraintes :** Ne pas précharger tout le pack ; tenir compte des formations/addon ; les profils ne prouvent pas que le chargement cause les chutes.
- **Décision par mesure :** Pics première arrivée versus arrivée chaude, compilation/upload et taux de miss.

**27. Préparation CPU parallèle des modèles — Fort sur pics si compilation responsable**

- **Point de départ :** Caches et chemins de chargement identifiés ; thread-safety des étapes à auditer.
- **Modification :** Jobs bornés pour lecture/décodage/assemblage CPU, snapshots immuables et publication.
- **Contraintes :** OpenGL reste sur contexte autorisé ; fonctions addon, fournisseurs et générateurs partagés pas présumés thread-safe.
- **Décision par mesure :** Temps render thread, attente jobs et taille backlog ; ne pas saturer CPU terrain.

**28. Caches résidents pilotés par budget — Fort sur pics si churn**

- **Point de départ :** CachedResource expire ; VehicleModel.MODEL_LIFESPAN=60000 ; textures ont aussi expiration.
- **Modification :** Retenir les ressources fréquentes selon budgets RAM/VRAM et fréquence réelle ; éviction graduelle.
- **Contraintes :** Éviter mémoire illimitée et reconstruction répétée ; mesurer l'ownership/destruction GPU.
- **Décision par mesure :** Miss/recompiles, mémoire native/VRAM et p95/p99 d'arrivées répétées.

**29. Géométrie partagée entre variantes — Conditionnel fort**

- **Point de départ :** VehicleResource construit variantes portes fermées, bogies, conditions et formations.
- **Modification :** Partager les sous-maillages immuables identiques ; variantes pointent sur des plages communes.
- **Contraintes :** Références comptées, durée de vie par génération ; ne pas fermer deux fois un VAO partagé.
- **Décision par mesure :** Octets RAM/VRAM, temps upload et nombre de copies ; reload et éviction.

### Affichages et textures

**30. Atlas de glyphes et textes mis en cache — Conditionnel fort en gares riches en textes**

- **Point de départ :** DynamicTextureCache rasterise des ressources/textes ; RenderPIDS dessine les informations.
- **Modification :** Réutiliser glyphes et layout au lieu de refaire des images entières lorsque cela s'y prête.
- **Contraintes :** Police/CJK, kerning, style et pixels doivent rester cohérents ; plusieurs chemins d'affichage existants.
- **Décision par mesure :** Rasterisations, textures/binds et temps génération ; affichages français/CJK.

**31. PIDS et plans recalculés uniquement sur changement — Conditionnel**

- **Point de départ :** ArrivalsCache et cache auto-PIDS existent ; ce n'est pas un ajout de cache depuis zéro.
- **Modification :** Versionner layout/données/route ; séparer texte stable, secondes et animation de défilement.
- **Contraintes :** Rafraîchir les arrivées et affichages exactement quand nécessaire ; ne pas figer les données pour gagner du temps.
- **Décision par mesure :** Mises à jour de layout et temps PIDS ; gare avec nombreux écrans.

**32. Mise à jour partielle des textures dynamiques — Conditionnel fort si uploads coûteux**

- **Point de départ :** DynamicTextureCache.java:277–303 crée une nouvelle texture pour le résultat.
- **Modification :** Réutiliser des allocations et transférer les rectangles réellement modifiés.
- **Contraintes :** Dimensions/format/génération doivent correspondre ; UV et autres utilisateurs inchangés.
- **Décision par mesure :** Textures créées, octets transférés et temps GPU/driver ; modification locale de panneau.

**33. Transferts GPU prioritaires et bornés — Fort sur pics si uploads responsables**

- **Point de départ :** Enregistrement de textures via resourceRegistryQueue ; uploads GPU modèles à mesurer.
- **Modification :** Budgeter les uploads par image, priorité aux ressources visibles/proches ; PBO/anneaux seulement si utiles.
- **Contraintes :** Ancienne ressource valide conservée jusqu'au remplacement ; pas de longues attentes ni famine des tâches.
- **Décision par mesure :** Durée max upload/image, retard d'affichage et p99 ; arrivée/reload.

### CPU et simulation

**34. File de rendu structurée et réutilisable — Conditionnel, fondation**

- **Point de départ :** VehicleResource.java:521 callbacks ; StoredMatrixTransformations.copy ; pools de callbacks déjà présents.
- **Modification :** Remplacer les données temporaires dominantes par tableaux/commandes compacts et snapshots possédés par l'image.
- **Contraintes :** Pooling déjà partiel ; ne pas réutiliser un objet encore en file ; aucun modèle ni état partagé mutable.
- **Décision par mesure :** Octets alloués/image, pause GC et temps queue ; JFR/allocations à obtenir.

**35. Simulation client indépendante du nombre d'images — Conditionnel, refonte sensible**

- **Point de départ :** MainRenderer.java:99 simule les véhicules à chaque rendu principal.
- **Modification :** Calculer l'état à cadence définie et interpoler une pose graphique fluide par image.
- **Contraintes :** Préserver mouvement réel, monture, collisions, manuel, portes et correction réseau ; modification core possible.
- **Décision par mesure :** Temps simulation client et frame time ; tests de gameplay et extrapolation.

**36. Préparation parallèle des poses et données graphiques — Conditionnel**

- **Point de départ :** RenderVehicles reconstruit listes/positions ; travail parallèle existant surtout pour culling/textures.
- **Modification :** Produire les poses graphiques indépendantes sur jobs à partir d'un snapshot stable.
- **Contraintes :** Aucun accès monde mutable/GL depuis jobs ; scheduling peut coûter davantage ; trajet monté reste cohérent.
- **Décision par mesure :** Temps jobs+join et render thread ; véhicules nombreux, pas seulement une voiture.

**37. Éclairage réutilisé avec invalidation locale — Conditionnel**

- **Point de départ :** RenderRails.java:288/312 lit lumière par segment ; véhicules lisent à leurs positions.
- **Modification :** Rails : lumière par secteur/point avec versions ; trains : réutiliser uniquement tant que cellule/éclairage valides.
- **Contraintes :** Cycle ciel, blocs lumineux, déplacement et éclairage addon doivent invalider ; ne pas figer lumière.
- **Décision par mesure :** Lectures de lumière et temps total ; train traversant zones éclairées.

### Réseau et serveur

**38. Deltas réseau et identité des objets conservée — Conditionnel, hors preuve de rendu actuelle**

- **Point de départ :** PacketUpdateVehiclesLifts.java:98–118 parcourt/recrée des objets mis à jour.
- **Modification :** Appliquer des deltas cohérents aux états stables et limiter les données par intérêt client.
- **Contraintes :** Compatibilité protocole, maintien des IDs/interpolations et resynchronisation ; pas moins de mises à jour utiles.
- **Décision par mesure :** Octets/paquets, temps traitement et allocations ; capture réseau/tick nécessaire.

**39. Graphe de routes et recherches incrémentales — Conditionnel, core à auditer**

- **Point de départ :** Aucun profil serveur fourni ; moteurs de simulation core hors audit approfondi actuel.
- **Modification :** Réutiliser résultats de routes/recherches avec invalidation des sous-graphes modifiés.
- **Contraintes :** Ne pas cache des résultats après changement de rails/signalisation ; bénéfice FPS indirect en solo seulement si contention.
- **Décision par mesure :** Spark serveur, ms/tick et requêtes graphe ; mêmes décisions de circulation.

**40. Simulation et recherches spatiales à grande échelle — Conditionnel, core à auditer**

- **Point de départ :** Index rails client déjà dans V4 ; index serveur et coût simulation non établis.
- **Modification :** Broad-phase spatiale pour recherches/collisions/signaux ; échéances pour tâches réellement événementielles.
- **Contraintes :** Conserver ticks nécessaires, signaux, conflits, horaires et gameplay ; aucun train invisible désactivé arbitrairement.
- **Décision par mesure :** ms/tick, requêtes spatiales et exactitude simulation ; serveur très grand réseau.

## Fondations utiles, sans les présenter comme des gains spectaculaires

- **S1 — Banc V2/V4/Work reproductible :** JAR SHA, scène/caméra/options identiques, plusieurs captures chaudes et premières arrivées ; médiane et p95/p99 des millisecondes/image.
- **S2 — Compteurs de contribution par famille :** Rails, train par modèle, décor, texte : dessins, triangles, matériaux, applications shaders, candidats/culls, bytes upload ; instrumentation actuelle seulement partielle.
- **S3 — Temps GPU asynchrones et allocations :** Queries disponibles lues sans attente ; distinguer travail Java, attente driver, GPU et GC. Les profils Spark seuls ne suffisent pas.
- **S4 — Invalidation/versionnement partagé :** Générations monde/dimension/pack, changements de rails/blocs/lumière, caméras/passes ; nécessaire aux caches et jobs.
- **S5 — Capacités matérielles et fallbacks :** Backend simple compatible pour Oculus/addons et matériel sans extensions ; pas d'obligation universelle de OpenGL 4.3/4.4.
- **S6 — Réduction des allocations locales :** Conserver hashes sans varargs, buffer matrice réutilisable, callback pools ; examiner wrappers/streams via allocations, pas présumer un gain spectaculaire.
- **S7 — Durée de vie et annulation des ressources :** Libérer NativeImage/buffers et tâches de générations périmées ; profil mémoire pour prouver fuites/churn ; pas d'accusation de fuite sans preuve.
- **S8 — Validation visuelle et fonctionnelle :** Mixin réellement appliqués en jeu ; Oculus on/off, France Addon, vitres, ombres, portes/bogies, camera embarquée, F3+T, éditions, dimensions ; CI ne remplace pas cela.

## Pistes examinées mais exclues comme promesses de performance

- **Sauter ShaderInstance/Oculus.apply et ne charger que MODEL_VIEW_MATRIX :** déjà dangereux dans V3 ; matrices inverses/normales, uniformes custom, textures et framebuffers peuvent devenir incohérents.
- **Supprimer systématiquement les VAO binds/cache GL global :** état partagé avec Minecraft, Oculus et addons ; économies apparentes contre bugs de rendu.
- **Fusionner toutes les pièces/tous les matériaux :** incompatible avec animations, lumière, transparence et conditions. Une fusion très grande peut aussi dégrader culling et coût GPU.
- **Désactiver trains, intérieurs ou ombres :** réduction de contenu/qualité, pas un renderer équivalent plus rapide. Les options LOD sont explicitement des compromis.
- **Remplacer OpenGL par Vulkan dans MTR seulement :** refonte de l'écosystème de rendu et de l'intégration Oculus, aucune solution directe démontrée ici.
- **DLSS/FSR, baisse résolution, frame generation :** choix de rendu global/qualité ; ne suppriment pas les soumissions MTR qui chargent le CPU. Pas un changement MTR autonome dans ce chantier.
- **Ajouter des threads partout ou déplacer tous les appels OpenGL sur un worker :** contexte et données non thread-safe ; contention possible avec les autres mods.
- **Augmenter la RAM, collectionneur JVM exotique, empaqueter du JNI ou optimiser chaque getter sans profil :** pas de preuve d'un changement d'échelle dans les captures. JFR/mesures de mémoire avant intervention.
- **Cache d'image entière/omettre des frames parce que la caméra ne bouge pas :** monde, animations et temporalité shaders continuent à évoluer ; ne préserve pas le rendu.
- **GPU-driven/meshlets/bindless comme slogans :** les API ne suffisent pas. Meshlets possibles dans un futur prototype de visibilité ; pas de mesh shaders supposés disponibles sous le backend OpenGL du pack. Bindless peut changer les accès textures mais demande formats/shaders/capacités et ne résout pas le batching seul.
- **Attendre une query GPU ou faire un readback à chaque image :** peut créer le blocage que l'optimisation tente d'éviter.

## Sources locales vérifiées

Les lignes sont celles du snapshot inspecté ; les fichiers sont relatifs au dépôt.

- [Analyse Spark et bytecode livré](mtr-v4-work-optimization.md).
- [MainRenderer](../fabric/src/main/java/org/mtr/mod/render/MainRenderer.java) : simulation par rendu principal, orchestration main/shadow et queues.
- [RenderRails](../fabric/src/main/java/org/mtr/mod/render/RenderRails.java) : sélection, lumière, transformations et soumissions répétées.
- [RenderVehicles](../fabric/src/main/java/org/mtr/mod/render/RenderVehicles.java) : poses avant le test de visibilité, portes/bogies et préparation par passe.
- [WorkerThread](../fabric/src/main/java/org/mtr/mod/render/WorkerThread.java), [CachedCullingDataProvider](../fabric/src/main/java/org/mtr/mod/render/CachedCullingDataProvider.java), [BoundedOcclusionCullingInstance](../fabric/src/main/java/org/mtr/mod/render/BoundedOcclusionCullingInstance.java) : queues coalescées, culling borné et cache d'opacité existants.
- [VehicleResource](../fabric/src/main/java/org/mtr/mod/resource/VehicleResource.java), [DynamicVehicleModel](../fabric/src/main/java/org/mtr/mod/render/DynamicVehicleModel.java), [OptimizedModelWrapper](../fabric/src/main/java/org/mtr/mod/resource/OptimizedModelWrapper.java) : regroupement existant par condition, variantes et modèles assemblés.
- [StoredMatrixTransformations](../fabric/src/main/java/org/mtr/mod/render/StoredMatrixTransformations.java), [CachedResource](../fabric/src/main/java/org/mtr/mod/resource/CachedResource.java), [VehicleModel](../fabric/src/main/java/org/mtr/mod/resource/VehicleModel.java) : callbacks, copies, durée de vie des modèles.
- [CustomResourceLoader](../fabric/src/main/java/org/mtr/mod/client/CustomResourceLoader.java), [DynamicTextureCache](../fabric/src/main/java/org/mtr/mod/client/DynamicTextureCache.java), [RenderPIDS](../fabric/src/main/java/org/mtr/mod/render/RenderPIDS.java) : préchargement et caches/worker texture déjà existants.
- [PacketUpdateVehiclesLifts](../fabric/src/main/java/org/mtr/mod/packet/PacketUpdateVehiclesLifts.java) : traitement des IDs et remplacement d'objets. Le coût réseau/core serveur reste à profiler.

## Références primaires et portée

- Les arènes/régions et lots constituent une architecture réelle de renderer : [RenderRegion de Sodium](https://github.com/CaffeineMC/sodium/blob/dev/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion.java). Référence d'architecture récente, **pas** une API directement transplantable dans Forge 1.20.1/Embeddium.
- [Khronos : ARB_multi_draw_indirect](https://github.com/KhronosGroup/OpenGL-Registry/blob/main/extensions/ARB/ARB_multi_draw_indirect.txt) définit les commandes multiples indirectes. Leur adoption est conditionnée par les capacités et l'organisation des états/données du renderer.
- [Khronos : ARB_buffer_storage](https://github.com/KhronosGroup/OpenGL-Registry/blob/main/extensions/ARB/ARB_buffer_storage.txt) et [buffer streaming](https://wikis.khronos.org/opengl/Buffer_Object_Streaming) décrivent stockage persistant et synchronisation. Piste 09 : besoin de fences/plages non recouvrantes, pas une suppression magique des attentes.
- [Meshoptimizer](https://github.com/zeux/meshoptimizer) décrit optimisation cache/fetch/overdraw et simplification. Piste 23 sans perte d'attributs ; piste 22 avec compromis de détail. Ce chantier n'a pas intégré cette bibliothèque.
- [Iris : compatibilité des shaders et vertex formats personnalisés](https://github.com/IrisShaders/Iris/blob/26.1/docs/development/compatibility/core-shaders.md) justifie les contraintes de 05/08/10/12/20. Cette documentation récente décrit le problème général ; l'Oculus 1.8.0 installé a été inspecté séparément dans le chantier initial.

## Validation et limites

Aucune des 40 pistes n'est annoncée comme apportant un gain FPS démontré. Pas de nouvelle compilation pour une carte documentaire. Les checks CI du JAR précédent prouvent compilation, géométrie et quelques contrats statiques, pas application en jeu, fidélité visuelle ni performance.

Pour chaque prototype : mesurer la scène chaude et la première arrivée, shaders on/off, main/shadow séparés ; garder les mêmes paramètres et hash des assets ; enregistrer frame times, nombre de dessins/applications shader, temps CPU/GPU et mémoire. Retenir seulement les changements dont le gain net dépasse la variabilité entre captures et qui passent les scènes de compatibilité.

Les gains de 01/04/05/07/12 ciblent en partie le même coût : ne pas les multiplier. Après réduction des soumissions, le GPU/shader pack ou le terrain Minecraft peut devenir dominant. Le plafond réel dépend de la machine et de la scène.

