# Suivi du chantier des 40 pistes

Demande du 11 octobre 2026 : implémenter la cartographie. Référence en jeu donnée par l'utilisateur : ancienne survie avec deux lignes de métro, grande gare centrale avec correspondances. Le serveur/monde exact, la caméra et le scénario reproductible ne sont pas encore identifiés.

Ce suivi distingue code écrit, CI, validation en jeu et idées restantes. Aucune ligne ne vaut une annonce de gain FPS.

## Lot 1 — en compilation

- 03 : compilation commune OBJ/Blockbench pour un même PartCondition. Les matériaux opaques égaux sont réunis avant upload ; pièces translucides/glow conservées séparées. Pas de fusion entre voitures/portes/bogies. Fallback si les accessors Forge ne sont pas appliqués. Option : `-Dmtr.v4.mergeMixedModels=false`.
- S2 : attribution des maillages soumis/dessinés et triangles à rails/trains/décors/autres, séparée shaderless/main/shadow. Tag capturé dans la RenderCall : une ressource dessinée dans deux familles ne dépend pas d'une table globale par VAO.
- S3 : timestamps GPU optionnels, maximum 32 paires, consultation de disponibilité avant lecture, polling borné et aucun wait. `-Dmtr.v4.gpuMetrics=true` implique diagnostics ; fallback si OpenGL 3.3 absent. Les timestamps mesurent un intervalle GPU de passe, pas les cycles occupés exclusivement par MTR ; résultats différés.
- Mesure des chemins de matrice qui appellent le shader Minecraft/Oculus, sans contourner son application. Le compteur est nommé matrix-shader-paths, pas nombre total de tous les apply d'Oculus.
- Tests locaux : helper GPU borné/nonbloquant, capacité absente, instrumentation désactivée, portée des familles et imbrication main/shadow ; cache d'attributs et interrupteur.
- Checks de bytecode ajoutés pour MaterialGroup/CapturingVertexConsumer/RenderCall ; tests de géométrie enrichis pour lumière/couleur/UV/normales.
- Limites : familles du renderer optimisé seulement ; panneaux/textes du renderer traditionnel ne sont pas encore couverts. Aucun timer par famille GPU. Aucun test visuel de ce lot à ce stade.

## État de l'inventaire

03, S2 et S3 sont partiellement implémentés, en validation CI. Les 39 autres pistes ne sont pas déclarées terminées. S1 et S8 exigent le scénario réel et des captures comparables. S4–S7 sont des fondations à développer avec les prochains lots.

L'ordre est celui de [la cartographie](mtr-performance-map-fr.md). Les pistes conditionnelles nécessitent leurs compteurs/profils pour choisir une architecture et vérifier le gain net ; le code compilé ne suffit pas.

## Points techniques vérifiés dans le JAR livré

- MaterialGroup contient materialProperties et modelPartConsumers ; chemin original : beginStage → consumers → triangulate → distinct → upload.
- Le constructeur composé OptimizedModel concatène uploadedParts : il ne consolide pas OBJ et Blockbench entre eux.
- RawMesh.append partage les objets Vertex et copie les indices de faces avec offset. Pour toute future transformation/baking, copier les sommets avant modification ; ne pas supposer un deep copy.
- L'API des buffer queries est optionnelle. Aucune requête bloquante ajoutée au rendu normal.

