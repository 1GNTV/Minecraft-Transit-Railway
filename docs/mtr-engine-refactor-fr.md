# MTR Engine — refonte progressive du rendu Forge 1.20.1

## Objectif

Réduire le travail MTR **par image** plutôt que multiplier les optimisations locales.
Minecraft conserve ses chunks, son monde, ses RenderTypes et ses shaders ; MTR Engine
vise une scène ferroviaire *persistante* intégrée à Minecraft, **pas** un moteur
OpenGL indépendant qui contournerait Oculus/Iris.

**Référence de performance : V2** (branche `perf/forge-1201-render-pipeline`).
Ne jamais annoncer un gain contre V2 sans comparaison dans la même scène, mêmes
versions d'addons, mêmes shaders et sans profiler. Une compilation CI n'est
pas un benchmark FPS.

## Livraison E0 — plans statiques persistants (cette branche)

- `ScenePlanCache` : plan CPU immuable par emplacement, modèle, snapshot, lumière
  et matrice. Cache LRU plafonné à 8192 plans, vidé au changement de monde et
  au reload. Les callbacks de frame, références au monde vivant et buffers GPU
  ne sont **pas** mis en cache.
- `StaticRegionRenderer` : réutilise les sélections de fragments et placements
  de pages existants pour rails et Eye Candy. Ne rappelle plus
  `RegionPageLayout.page` pour les objets inchangés à chaque image.
- Le masque de plages par matériau d'une page GPU est conservé lorsque
  la sélection visible est identique ; les sélections changées recalculent
  le masque. Le `RegionVertexRanges.select` par passe reste nécessaire.
- Cache GPU et plan CPU ont des durées de vie **indépendantes**. Éviction CPU
  sans fermeture des ressources GPU ; fermeture GPU uniquement par son
  propriétaire. Purge ensemble en cas de remise à zéro des pages.
- En cas de page absente, un **objet entier** repasse au renderer original ;
  jamais de sous-modèle partiellement dessiné + doublon.
- `ScenePlanCache` construit atomiquement : échec de reconstruction = ancien
  plan conservé, pas de plan partiellement publié.

### Interrupteurs A/B sans modification d'addons

- `-Dmtr.engine.scenePlans=false` : reconstruit les plans CPU à chaque
  soumission, en conservant le système de régions antérieur.
- `-Dmtr.engine.visibilityMasks=false` : reconstruit la sélection par matériau
  à chaque frame au lieu de réutiliser les masques.
- Les options n'affectent ni la simulation des trains, ni les interfaces d'addons,
  ni le rendu des shaders ; elles facilitent l'isolement d'une régression.

**Ce que E0 ne fait PAS :** supprimer le parcours par frame des rails,
accélérer les shaders par magie, remplacer le système de commandes de MTR ou
assurer déjà la victoire sur V2. La construction de matrices d'entrée et
les opérations de sélection visibles sont encore faites.

## E1 — scène par secteurs et invalidation événementielle

Proposition, non implémentée :
1. Registre `SceneSector` indépendant des RenderCallbacks ; poignées stables
   sur rails, décorations et interfaces statiques.
2. Capturer la géométrie une fois au chargement et stocker une commande GPU
   par maillage / matériau / région au lieu d'une séquence de lambdas par frame.
3. Révisions séparées : topologie de rail, transformation, ressource, matériau,
   éclairage (sky/block), monde/dimension, options de rendu.
4. Frustum/occlusion sur blocs de commandes ; sélection par ensemble de handles
   et plages d'indices, avec pages visibles déjà compilées.
5. Éditeur MTR : previews/rails fantômes et overlays restent sur chemin dynamique.

Garde-fou : ne pas garder un niveau de lumière figé entre jour/nuit ou mises à
jour de chunks ; surveiller invalidations et budget GPU/CPU.

## E2 — train simulation/interaction vs visuel

Proposition, non implémentée :
- Construire un état `TrainFrame` *sans le coupler aux passes Oculus main/shadow*.
- Maintenir portes, sons, embarquement, position du joueur, gangways, bogies et
  surfaces de raccordement pour wagons proches, **même invisibles**.
- Émettre ensuite des commandes visuelles séparées, par voiture/sous-mesh.
- Exposer bounds conservateurs des portes, vitres et animations.
- Préserver les points d'injection `RenderVehicles.renderConnection` de
  TJMetro 1.1.0 : méthode/descriptor exact et effets de bord.
- Ne jamais changer signatures publiques et ABI d'addon sans adaptateur validé.

## E3 — soumission GPU optimisée sous Oculus

Proposition, non implémentée :
- Préparer un flux de commandes opaques avec matériaux, état GL et dépendances
  d'ordre explicites ; grouper les commandes commutatives prouvées sûres.
- Instance transforms + couleur/lumière/overlay individuels ; conserver toutes
  les matrices dérivées et le `ExtendedShader.apply` d'Oculus.
- Ajouter compteurs de refus d'instancing : ordre, alpha, VAO, shader, données,
  tampon plein, bounds inconnus. *Mesurer avant de modifier les seuils.*
- Multi-draw et arène de buffers seulement lorsque l'overhead CPU est prouvé
  dominant, sans contourner shader/Framebuffer/sampler.
- Le renderer original est un fallback obligatoire pour transparence,
  programmes shader inconnus, GL4ES, addons et contextes spéciaux.

## E4 — affichages et LOD

Proposition, non implémentée :
- Textes JCM/PIDS : layout et glyphes mis en cache par contenu/style/langue,
  scroll par transformation, invalidation des ETA et reload.
- Modèles : clusterisation bounds + niveaux de détails simplifiés hors des
  coutures UV, silhouettes, vitres, portes et animations.
- Ombres : classes de détail propres à la projection de shadow pass.
- Hi-Z/GPU-driven seulement si le profil GPU en confirme l'intérêt.

## Matrice de validation bloquante

Pour déclarer une version acceptable :

- JAR Forge 1.20.1 compilé ; JUnit, tests du cache, comparaisons de pixels GL ;
  vérification des mixins et contrats binaires addons.
- Démarrage réel avec **TJMetro 1.1.0**, **MTR France Addon**, JCM et le modpack
  concerné ; pas de crash ou de mixin manquant.
- Avec **Oculus 1.8.0**, shaders on/off : train entrant, portes, bogies,
  connexions, intérieurs derrière les fenêtres, ombres, panneaux défilants.
- F3+T, changement de monde/dimension, destruction/modification de rails,
  lumière jour/nuit et rechargement du resource pack : aucun objet figé.
- Profil 60 secondes, trois répétitions, même caméra/même scène, 1% low et
  frametimes, décompte draws CPU/GPU et VRAM. V2 / branche de base / MTR Engine
  dans les mêmes conditions. Spark **après** les comparaisons sans profiler.
- Possibilité de revenir à V2 sans migration de monde.

Ne fusionner aucun lot qui régresse significativement face à V2 sans preuve
d'un correctif de stabilité/compatibilité compensateur. Le plan initial et ses
priorités pourront changer selon les mesures utilisateur.
