# Next : les cinq changements de rendu

Branche `work/mtr-v4-optimization`, basée sur V4. La V2 `dadf9c9` reste intacte. Cette version est une candidate à tester ; compiler ne mesure ni les FPS ni la compatibilité visuelle en jeu.

| Chantier | Mécanisme ajouté | Repli / limite |
| --- | --- | --- |
| Instancing Oculus | Transformation des sources GLSL déjà préparées par Oculus 1.8.0 ; matrices modèle, inverse et normales par instance ; séries consécutives de 4 à 8 objets solides compatibles. Le shader, ses textures, uniformes personnalisés et son application complète restent actifs. | Précompilation du candidat avant adoption. Géométrie/tessellation, GLSL ancien, macros utilisant les matrices, objets transparents/glow et états incompatibles restent ordinaires. Pas de contournement V3 de ShaderInstance.apply. |
| Visibilité train / voitures / pièces | Capture du frustum Minecraft de la passe ; union du train puis bounds des voitures avant chargement des ressources et lumière. Poses géométriques sans lumière ; seule la voiture traitée demande son éclairage. Test de clip conservateur par mesh avant ses attributs/dessin sur le chemin shaderless. | Frustum absent/autre passe, données invalides, train monté et proximité d'interaction/audio : traitement conservé. La simulation continue pour tous les trains. Les shaders avec déformation gardent le chemin de pièces ordinaire. |
| Régions persistantes rails / décorations | Géométrie solide, transformations et lumière par membre dans des pages GPU locales de 16³ blocs. Un changement de visibilité sélectionne les plages d'indices avec multi-draw, sans recompiler les sommets. Ajout/modèle/transformation/lumière changés reconstruisent la page ; ressources déjà mises en file restent protégées. | 64 pages / 256 000 sommets / une construction de 16 000 sommets par passe et famille. Les contenus incompatibles restent individuels. Les objets cachés peuvent rester résidents sans être dessinés. Il ne s'agit pas d'une arène GPU universelle ni de Hi-Z. |
| LOD des trains / ombres | Deux buffers d'indices partageant les sommets d'origine : retrait des composantes isolées de moins de 12,5 cm à 160 blocs, ou 25 cm à 64 blocs pour les ombres. Triangles restants, winding, UV et positions inchangés. Distance réelle à la caméra enregistrée lors de la soumission ; train monté en détail complet. | Aucun LOD si le mesh disparaîtrait, est transparent/glow, ne réduit pas au moins 10 % de ses indices ou dépasse les budgets. 8 Mio d'indices supplémentaires maximum. Ce n'est pas un simplificateur universel de surfaces continues. |
| Préchargement et CPU parallèle | Préparation anticipée des trains proches / approchants jusqu'à 320 blocs ; deux workers pour Blockbench, OBJ/MTL et conversion MQO/MQOZ, à partir de snapshots de fichiers. Atlas, transformation finale et GL restent sur le thread de rendu. Une étape de cache non forcée après l'image. | Deux demandes de lecture par image, file de 16 tâches, 18 préparations et 8 millions de caractères source maximum ; modèles de plus de 200 000 sommets exclus, 500 000 sommets préparés en attente au maximum, expiration à 5 secondes. Ressource indisponible/préparation tardive : chargement habituel. Pas de prédiction de parcours réseau ni de cache disque. |

Les caches de ressources utilisent désormais des références faibles pour ne pas retenir toutes les générations anciennes. Les budgets bornent les nouveaux chemins ; ils ne représentent pas un gestionnaire de toute la VRAM du jeu.

Suite après le premier test en jeu : [surcoûts corrigés et priorités](mtr-next-profile-followup-fr.md).

## Réglages d'essai

| Paramètre JVM | Défaut | Fonction |
| --- | --- | --- |
| `-Dmtr.next.oculusInstances=false` | true | Conserver les programmes Oculus sans branche d'instances. |
| `-Dmtr.next.visibility=false` | true | Désactiver le rejet train/voiture. |
| `-Dmtr.next.partVisibility=false` | true | Désactiver le rejet par mesh shaderless. |
| `-Dmtr.next.regions=false` | true | Revenir aux rails et décorations individuels. |
| `-Dmtr.next.lod=false` | true | Conserver tous les détails des trains et de leurs ombres. |
| `-Dmtr.next.lodDistance=160` | 160 | Distance du LOD principal, minimum 32 blocs. |
| `-Dmtr.next.shadowLodDistance=64` | 64 | Distance du LOD des ombres, minimum 32 blocs. |
| `-Dmtr.next.preload=false` | true | Désactiver anticipation et workers de modèles. |
| `-Dmtr.v4.metrics=true` | false | Compteurs de soumission, triangles et familles. |
| `-Dmtr.v4.gpuMetrics=true` | false | Mesures GPU différées disponibles sur le matériel compatible. |

## Validation

Code compilé : `db74325bcdb362322c4df000057a58b38ae98fcf`. [GitHub Actions 38153720748](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38153720748) réussi : compilation Forge 1.20.1, sept groupes de checks Python, quinze tests de géométrie (zéro échec, zéro ignoré), vérification des classes et contrats statiques du JAR. Les logs complets ont été inspectés ; aucune erreur de compilation, deux avertissements Javadoc hérités et avertissements de dépréciation des outils CI.

Le job OpenGL passe sur Mesa llvmpipe : pixels identiques entre dessins ordinaires et huit instances (matrices, inverses, normales, uniformes personnalisés et sampler), retour au mode ordinaire, refus de shaders incompatibles, régions complètes et sélection partielle de plages. Ces tests utilisent le vrai contexte GL et les helpers du mod avec des substituts Minecraft ; ils ne prouvent pas l'application des mixins dans une session Oculus.

[Artefact Forge](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/38153720748/artifacts/11708115777) récupéré et vérifié : 84 421 820 octets, SHA256 `74816821665f136f46f803aca7659b9740f95521831f7ae54826b96b61ed4786`. ZIP SHA256 `497137e7214ee400e7e2e9f80ef66a4464fee70d6671f9ed4a8a8784f2debd1c`. Rapports récupérés : quinze tests réussis ; ZIP SHA256 `dc6647b2d6bf39d487cacfb8fa8db67a3b15152cd57ca8f5e65412c953af92e8`. Copie locale : `artifacts/MTR-forge-4.0.5+1.20.1-next-architecture-db74325.jar`. Version interne 4.0.5 conservée. Les commits documentaires suivants ne changent pas ce JAR.

Les références V2/V3/V4 ont été revérifiées inchangées le 11 octobre 2026. Aucun gain FPS ni résultat visuel avec MTR France Addon n'est revendiqué.

Le point d'injection Oculus a été vérifié contre le JAR public `oculus-mc1.20.1-1.8.0.jar`, et non seulement contre une branche GitHub. Il faut encore vérifier l'application des mixins et le résultat visuel dans Minecraft avec le pack de shaders et MTR France Addon utilisés. Aucune installation du profil ou lecture du monde privé n'est effectuée ici.

Les deux profils Spark précédemment décodés sont des captures V4 du thread de rendu, pas une mesure V2 ni une mesure GPU. Ils justifient l'étude de la soumission et des états ; ils ne prouvent pas un gain de ces nouveaux chemins.

Pour comparer, utiliser la grande gare centrale déjà indiquée, la même vue et les mêmes réglages : gare au repos puis train entrant, sans shaders et avec shaders. Conserver FPS moyen, percentiles de temps d'image et défauts visuels. Aucun chiffre Next n'est annoncé avant cette comparaison.
