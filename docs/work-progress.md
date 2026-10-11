# Suivi du chantier des 40 pistes

Demande du 11 octobre 2026 : implémenter la cartographie. Référence en jeu donnée par l'utilisateur : ancienne survie avec deux lignes de métro, grande gare centrale avec correspondances. Le serveur/monde exact, la caméra et le scénario reproductible ne sont pas encore identifiés.

Ce suivi distingue code écrit, CI, validation en jeu et idées restantes. Aucune ligne ne vaut une annonce de gain FPS.

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

## État de l'inventaire

01, 03, 04, 19, 24, 28, 33 et S2–S5/S7 ont une réalisation partielle ou un prototype. Aucune des 40 pistes n'est déclarée entièrement validée. S1 et S8 exigent le scénario réel et des captures comparables. Les autres pistes restent étudiées ou à développer selon les nouvelles mesures.

L'ordre est celui de [la cartographie](mtr-performance-map-fr.md). Les pistes conditionnelles nécessitent leurs compteurs/profils pour choisir une architecture et vérifier le gain net ; le code compilé ne suffit pas.

## Points techniques vérifiés dans le JAR livré

- MaterialGroup contient materialProperties et modelPartConsumers ; chemin original : beginStage → consumers → triangulate → distinct → upload.
- Le constructeur composé OptimizedModel concatène uploadedParts : il ne consolide pas OBJ et Blockbench entre eux.
- RawMesh.append partage les objets Vertex et copie les indices de faces avec offset. Pour toute future transformation/baking, copier les sommets avant modification ; ne pas supposer un deep copy.
- L'API des buffer queries est optionnelle. Aucune requête bloquante ajoutée au rendu normal.

