# Comparer V2 et Work localement

La scène de référence est la grande gare centrale de l'ancienne survie à deux lignes de métro. Aucun nom de serveur, adresse ou coordonnées n'est nécessaire pour conserver les résultats localement. Aucune mesure en jeu n'a encore été réalisée par ce chantier.

## Comparaisons séparées

| Scène | Sans shaders | Oculus + même shader pack |
| --- | --- | --- |
| Gare, caméra fixe, trains déjà chargés | V2 / Work | V2 / Work |
| Première arrivée après chargement | V2 / Work | V2 / Work |
| Arrivées suivantes, mêmes trains | V2 / Work | V2 / Work |
| Portes/bogies, vitres et affichages | V2 / Work | V2 / Work |
| Caméra dans le train | V2 / Work | V2 / Work |
| Rechargement de ressources puis retour à la gare | V2 / Work | V2 / Work |

Conserver résolution, distance de rendu, limite FPS/VSync, mods, packs, météo et angle de caméra. Faire au moins trois captures comparables par version, en alternant leur ordre. Une première arrivée est une mesure de chargement : elle doit rester séparée de la scène chaude. Conserver les longues images de la capture ; ne pas effacer un pic parce qu'il gêne le résultat.

Le hash du JAR et les options suffisent pour identifier chaque version. V2 reste un JAR séparé et intact. Les interrupteurs Work n'en font pas une reproduction binaire exacte de V2.

## Isoler un changement

| Variation Work | Option |
| --- | --- |
| Attributs génériques sans shaders | `-Dmtr.v4.attributeCache=false` |
| Fusion OBJ opaque | `-Dmtr.v4.mergeOpaqueObj=false` |
| Fusion de sources mixtes OBJ/Blockbench | `-Dmtr.v4.mergeMixedModels=false` |
| Budget de publication des textures | `-Dmtr.v4.textureUploadBudget=false` |
| Prototype de secteurs de rails (désactivé par défaut) | `-Dmtr.v4.railBatches=true` |
| Compteurs de passe/famille | `-Dmtr.v4.metrics=true` |
| Timestamps GPU sans attente | `-Dmtr.v4.gpuMetrics=true` |

Changer une option à la fois, avec redémarrage. Le prototype de rails ne s'active que dans le chemin shaderless/custom shader compatible ; avec un shader pack il conserve le rendu individuel. Vérifier surtout lumière des rails, précision sur pentes/courbes, previews, changements de styles, F3+T, changement de dimension et déconnexion.

## Lire les résultats

- Les compteurs Work couvrent le renderer optimisé : dessins/triangles par famille, matériaux, chemins d'application de shader par matrice et temps de passe sur le thread de rendu.
- Le temps de passe inclut les attentes du driver. Les timestamps GPU décrivent un intervalle différé pouvant contenir des creux/interleavings ; ce ne sont pas des cycles GPU occupés exclusivement par MTR.
- Ces compteurs et Spark ne mesurent pas directement les FPS présentés. Utiliser une capture réelle de durées entre images, puis exporter les millisecondes dans un CSV local. Indiquer explicitement la colonne fournie par le collecteur choisi ; vérifier qu'elle représente bien l'intervalle entre images.
- `tools/compare_frame_times.py` accepte deux captures et produit les moyennes, médianes, p95/p99, maximum, FPS moyens, 1% low et images dépassant 16,67/33,33 ms. Il rejette valeurs nulles/non finies et garde les grandes durées.

Exemple pour des fichiers dont la colonne s'appelle `frame_ms` :

```text
python tools/compare_frame_times.py v2.csv work.csv --scenario gare-arrivee-shaders --output comparaison.json
```

Une autre colonne peut être choisie avec `--frame-ms-column`. `--discard-first-samples` est explicite et apparaît dans le résultat ; par défaut, aucune image n'est exclue. Le 1% low est ici `1000 / moyenne des durées des ceil(1% × N) images les plus lentes`. Les conventions d'autres collecteurs peuvent différer : comparer la même définition.

Les tests de cet outil emploient des traces **synthétiques**, notamment 990 images de 10 ms et 10 images de 100 ms. Elles vérifient que médiane stable et saccades sont distinguées ; elles ne décrivent aucune performance de Minecraft.

Le rapport est descriptif et ne conclut pas automatiquement à un gain significatif. Vérifier répétitions, variabilité et fidélité visuelle avant d'adopter un prototype. Les gains des pistes de batching, instancing et multi-draw se recouvrent : ils ne s'additionnent pas comme des multiplicateurs FPS.
