# Suivi du chantier des 40 pistes

Demande du 11 octobre 2026 : implémenter la cartographie. Référence donnée par l'utilisateur : ancienne survie avec deux lignes de métro, grande gare centrale avec correspondances. L'utilisateur souhaite garder les détails de son monde privés ; la suite du chantier utilise les profils fournis et les sources. Aucune mesure en jeu n'est revendiquée.

Ce suivi distingue code écrit, CI, validation en jeu et idées restantes. Aucune ligne ne vaut une annonce de gain FPS. Le [tableau des quarante statuts](mtr-performance-status-fr.md) explicite chaque réalisation et chaque reste à faire.

## Dernier artefact vérifié

Code `500334ef0e70f67d6f57617cdd0b1e01a495cbac`, [CI 38140845544](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38140845544) : compilation, cinq groupes de checks Python, sept tests de géométrie (zéro échec/ignoré), intégrité et contrats statiques du JAR réussis. Logs complets inspectés : aucune erreur, deux warnings Javadoc hérités. L'erreur de nom de matériau du lot 2 est corrigée.

[Artefact Forge](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38140845544/artifacts/11702746873), téléchargé et vérifié localement : JAR 84 342 424 octets, SHA256 `086654b36ddc33fe4af5a76022af48b695c2fb72fd409199af51db000c25dd94`. ZIP SHA256 `34ed354fa691f83c58515c469cc997f62de961d0168e02833b9f9db9e3cf01ff`. La copie locale est `artifacts/MTR-forge-4.0.5+1.20.1-work-v4.jar` ; les fichiers compilés privés restent hors Git.

Les modifications documentaires suivant ce code ne changent pas le JAR. Les références V2/V3/V4 ont été vérifiées inchangées. Pas de déploiement dans le profil installé ni de mesure FPS/validation Oculus/France Addon en jeu. Le prototype de rails reste désactivé par défaut.

## Lot 1 — CI réussie, en jeu non validé

- 03 : compilation commune OBJ/Blockbench pour un même PartCondition. Les matériaux opaques égaux sont réunis avant upload ; pièces translucides/glow conservées séparées. Pas de fusion entre voitures/portes/bogies. Fallback si les accessors Forge ne sont pas appliqués. Option : `-Dmtr.v4.mergeMixedModels=false`.
- S2 : attribution des maillages soumis/dessinés et triangles à rails/trains/décors/autres, séparée shaderless/main/shadow. Tag capturé dans la RenderCall : une ressource dessinée dans deux familles ne dépend pas d'une table globale par VAO.
- S3 : timestamps GPU optionnels, maximum 32 paires, consultation de disponibilité avant lecture, polling borné et aucun wait. `-Dmtr.v4.gpuMetrics=true` implique diagnostics ; fallback si OpenGL 3.3 absent. Les timestamps mesurent un intervalle GPU de passe, pas les cycles occupés exclusivement par MTR ; résultats différés.
- Mesure des chemins de matrice qui appellent le shader Minecraft/Oculus, sans contourner son application. Le compteur est nommé matrix-shader-paths, pas nombre total de tous les apply d'Oculus.
- Tests locaux : helper GPU borné/nonbloquant, capacité absente, instrumentation désactivée, portée des familles et imbrication main/shadow ; cache d'attributs et interrupteur.
- Checks de bytecode ajoutés pour MaterialGroup/CapturingVertexConsumer/RenderCall ; tests de géométrie enrichis pour lumière/couleur/UV/normales.
- CI [38138531205](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38138531205), commit `80d822ad00dd46d4e3858437595ffde75ce8b1fa` : build, helpers, géométrie et vérification du JAR réussis. Logs inspectés : aucune erreur ; deux warnings Javadoc hérités. Artefact disponible, ZIP SHA256 `59ed9daa891a80d8c0e3d9636d7e5bdc66dc737d9c0609a7ec1610ad4b6d726d`.
- Limites : familles du renderer optimisé seulement ; panneaux/textes du renderer traditionnel ne sont pas encore couverts. Aucun timer par famille GPU. Aucun test visuel de ce lot à ce stade.

## Lot 2 — prototype de secteurs de rails

- 01 / 04 / 24 : `-Dmtr.v4.railBatches=true`, désactivé par défaut. Capture CPU optionnelle lors du chargement du rail, copie des sommets/normales/indices avant baking ; regroupement par secteur de 16 blocs et matériau opaque égal. Transformations dans le même ordre Y/X/Z, positions relatives au secteur, lumière propre à chaque segment dans le buffer de sommets.
- La sélection actuelle de RenderRails est conservée intégralement : seule sa soumission est regroupée. Signature exacte (modèle, position, angles, lumière), pas seulement un hash. Deux images consécutives de sélection identique avant construction ; pendant une modification, rendu individuel.
- 19 / 28 / 33 / S7 partiels : une construction par image, maximum 16 000 sommets par construction, 64 entrées, 256 000 sommets GPU résidents ; capture CPU limitée à 200 000 sommets et 16 000 par modèle. Ce sont des plafonds structurels, pas une garantie de temps en millisecondes ou une comptabilité VRAM exacte. Éviction LRU sans fermer une ressource déjà soumise dans l'image. Fermeture des VAO **et** des vertex/index buffers à l'éviction, au rechargement, au changement de monde/dimension et à la déconnexion.
- S4 / S5 partiels : mode shader pack/shadow/gl4es non pris en charge ; repli vers le renderer MTR existant. Ressources contenant vitres, glow ou lumière imposée exclues entièrement. Aucun contournement de ShaderInstance/Oculus. Cette restriction ne valide pas la compatibilité générale du prototype.
- Tests locaux du vrai cache : stabilité, invalidation, ressources épinglées, plafond de construction/résidence, absence de tempête de retries après erreur et libération exacte. Tests de géométrie dans CI : copie indépendante, rotation/translation/normale, packing de lumière, UV/couleur et matériaux exclus.
- Limites : allocation/préparation des callbacks et lecture de lumière par segment restent présentes ; pas d'instancing, multi-draw, visibilité Hi-Z ou arène globale. La géométrie dupliquée peut augmenter la mémoire. Le coût de construction/transfert doit être comparé aux dessins supprimés dans la gare avant activation par défaut.

## Lot 3 — durée de vie des images et buffers

- 27 / 33 / S4 / S7 partiels : les textures terminées sont marquées par génération de rechargement. Une ancienne génération ferme son image plutôt que publier un résultat périmé. File de complétions limitée à 16 images ; au maximum deux complétions traitées par fenêtre de 16,67 ms (interrupteur `-Dmtr.v4.textureUploadBudget=false`). Cela borne le nombre de publications, **pas** le temps d'une grande publication ou la mémoire totale des images. La queue des tâches de génération CPU reste à borner selon les mesures.
- Le recadrage est déplacé du rendu vers le worker existant et ferme l'image source remplacée, y compris sur exception. Les erreurs de création/enregistrement ferment l'image ou la texture dont la propriété n'a pas été transférée. Les images nouvellement publiées reçoivent immédiatement leur TTL, avant la prochaine collecte.
- Chargement des polices sans boucle infinie ; une génération périmée ne réinstalle pas ses anciennes polices. Une police indisponible n'est pas remplacée silencieusement par un autre rendu.
- Les buffers créés par le compilateur Work ont désormais une propriété explicite : fermeture avec leur VAO, aliases suivis et nettoyage des buffers à la collecte du dernier propriétaire. Ceci ne prétend pas réparer l'ensemble de la durée de vie des ressources historiques des mappings. Le Cleaner des buffers n'efface pas les VAO historiques abandonnés sans close.
- Tests locaux sur les vrais helpers : libération unique avec aliases et Cleaner, image inchangée/transférée, recadrage, erreurs d'allocation/copie, capacité et budget de queue, invalidation concurrente de génération. Aucune annonce de correction d'un pic FPS mesuré.
- La CI du lot 2 a compilé les sources du mod, puis échoué sur un nom erroné du matériau glow **dans un test** (`CUTOUT_GLOW` au lieu de `CUTOUT_GLOWING`). Le nom est corrigé dans ce lot ; les sept tests de géométrie et le JAR final ont ensuite passé la CI.

## État consolidé

01, 03, 04, 19, 24, 27, 28, 33 et S2–S5/S7 ont une réalisation partielle ou un prototype. Aucune des 40 pistes n'est déclarée entièrement validée. S1 est outillé par [le protocole local et l'analyse de frame times](mtr-benchmark-fr.md) ; S8 reste une validation réelle. Les autres pistes restent étudiées ou à développer selon les nouvelles mesures.

Les changements architecturaux 05–10/12–23/25–26/29–32/34–40 ne sont pas annoncés comme implémentés. Plusieurs sont des alternatives ou dépendent d'un renderer/format de données nouveau ; les changements de shader, visibilité et simulation ne peuvent pas être certifiés par une compilation. La cartographie ne doit pas être confondue avec 40 gains acquis.

Dernière revue de sécurité de rendu : fermeture du scope GL de chargement de ressource et restitution de la pile de matrices via `finally`, même si un modèle/callback échoue. La CI suivante inclut également les tests du lecteur de frame times (traces synthétiques, aucun benchmark en jeu).

L'ordre est celui de [la cartographie](mtr-performance-map-fr.md). Les pistes conditionnelles nécessitent leurs compteurs/profils pour choisir une architecture et vérifier le gain net ; le code compilé ne suffit pas.

## Points techniques vérifiés dans le JAR livré

- MaterialGroup contient materialProperties et modelPartConsumers ; chemin original : beginStage → consumers → triangulate → distinct → upload.
- Le constructeur composé OptimizedModel concatène uploadedParts : il ne consolide pas OBJ et Blockbench entre eux.
- RawMesh.append partage les objets Vertex et copie les indices de faces avec offset. Pour toute future transformation/baking, copier les sommets avant modification ; ne pas supposer un deep copy.
- L'API des buffer queries est optionnelle. Aucune requête bloquante ajoutée au rendu normal.

