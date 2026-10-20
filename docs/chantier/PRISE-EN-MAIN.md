# Sillon Chantier — Forge 1.20.1

Le même JAR est nécessaire sur le client et le serveur. L’outil est réservé aux opérateurs en créatif.

## Dessiner une gare

1. Prendre **Outil Chantier** dans l’onglet principal MTR, ou `/give @s mtr:chantier`.
2. Cliquer droit pour placer le début de la station. Un aperçu suit la visée avant le second clic. Cliquer droit à nouveau pour fixer la longueur. Un clic dans le vide place le point à 12 m devant soi.
3. Tirer les poignées vertes avec le clic gauche : extrémités, largeur, hauteur. Maj + clic gauche sur une paroi de l’hologramme crée un accès dont on tire l’extrémité ; sur un plafond, il ajoute un étage. Les extrémités restent dans leur plan horizontal ; le niveau se règle séparément.
4. Molette : largeur. Maj + molette : hauteur. Ctrl + molette : longueur. Alt + molette : altitude.
5. **G** ouvre le petit panneau de réglages. Cette touche est modifiable dans les commandes Minecraft.
6. Dans **Chantier**, choisir **Construire / mettre à jour**. Échap enregistre le brouillon et revient dans le monde ; il ne déclenche jamais une excavation.

Le point choisi représente le niveau de la voie. Le sol porteur se trouve au-dessous ; les quais de station sont un bloc plus haut que la voie.

## Composer les volumes

L’onglet **Volumes** permet d’ajouter une station, un tunnel, une salle ou un escalier par deux nouveaux points. Les salles et tunnels en contact sont réunis avant de construire leur enveloppe : une cloison n’est pas laissée au milieu du raccordement.

- **Tirer un accès** ajoute un couloir latéral, ajustable avec ses poignées.
- **Ajouter un étage** ajoute une salle supérieure et une liaison par escalier. Les escaliers sont de vrais blocs d’escalier.
- **Tunnels entrée / sortie** crée les quatre amorces de tunnel correspondant aux deux voies de la station.
- **Suivre la voie visée** reprend la géométrie réelle d’un tronçon MTR, avec ses courbes et ses pentes. Viser la voie dans le monde avant d’ouvrir le panneau. Les tronçons longs sont divisés en volumes raccordés.
- **Suppr** dans les onglets Dimensions ou Volumes retire le volume sélectionné.
- Les volumes peuvent avoir un plafond plat, une voûte ou un profil arrondi. Les quais peuvent être latéraux ou centraux. L’éclairage et les piliers sont réglables.

L’aperçu est un hologramme de surfaces, visible à travers le terrain. Ses couleurs distinguent la structure, les quais et l’éclairage ; les textures définitives apparaissent après construction. Le bouton **Visiter l’aperçu** passe temporairement en spectateur pour traverser le terrain sans le modifier. **G**, puis **Quitter la visite**, rend le mode créatif et ramène au point de départ. Un retour est aussi prévu à la reconnexion si la visite a été interrompue.

## Matériaux

Deux palettes fournissent un point de départ. Les quatre identifiants de bloc sont modifiables : murs, sol, plafond, quai. Utiliser des identifiants tels que `minecraft:smooth_quartz` ou `mtr:platform`.

Les matériaux de l’enveloppe doivent être des blocs pleins, solides, sans liquide ni bloc-entité. Cette contrainte permet de fermer réellement le volume avant de le vider. Les escaliers utilisent actuellement le quartz lisse et les luminaires la lanterne aquatique.

## Terrain, eau et protections

Le serveur analyse l’ensemble du chantier avant tout changement. Pierre, andésite, terre, minerais reconnus par leurs tags et liquides sont traités sans filtre manuel. Les cavités sont isolées par les parois du chantier.

L’enveloppe est posée avant l’excavation ; l’intérieur est ensuite vidé, puis les quais, piliers et escaliers sont posés. Les extrémités des tunnels restent fermées tant qu’un autre volume ne les rejoint pas : un tunnel ouvert dans une poche d’eau ne serait pas étanche.

Les coffres, blocs techniques, blocs indestructibles et constructions non naturelles ne sont pas écrasés lors d’un nouveau chantier. Les nœuds MTR existants dans un espace à évider sont conservés. Un conflit de modification ou une zone non chargée empêche le placement ; un conflit pendant les travaux déclenche leur restauration.

Une modification de plan reconstruit seulement les blocs qui diffèrent. Une partie retirée retrouve son terrain d’origine. Les objets ajoutés ensuite dans les espaces inchangés sont préservés. Si la nouvelle géométrie exige d’écraser ces objets, la modification est refusée.

## Données MTR

Une station neuve crée une vraie gare MTR et ses deux voies de quai. Les tunnels neufs peuvent créer leurs voies ordinaires. Les nœuds permettent de poursuivre la ligne avec les outils MTR.

À proximité de voies ou gares déjà présentes, leurs données sont préservées : l’outil peut construire l’architecture autour sans s’approprier ce réseau. Les lignes, dessertes, horaires et dépôts se règlent toujours dans le dashboard MTR.

Les modifications purement architecturales qui conservent les voies préservent les données MTR existantes. Le déplacement ou la suppression de quais déjà desservis ou raccordés à des voies extérieures est refusé : retirer d’abord ces dessertes/raccordements permet de modifier leur géométrie sans casser une ligne active.

## Projets et annulation

Les brouillons sont conservés par serveur/monde et dimension. L’onglet Chantier permet de passer d’un projet enregistré à un autre. Ctrl + Z dans le monde annule un réglage du brouillon ; Ctrl + Maj + Z le rétablit.

**Restaurer le terrain initial**, puis confirmer, annule les travaux du projet entier, y compris les ajustements précédents et ses données MTR créées. Le serveur conserve les états initiaux dans un journal compressé, écrit avant le placement et remplacé de façon atomique. Le journal reste utilisable après un redémarrage.

Si des blocs ou données possédés par le chantier ont été modifiés entre-temps, la restauration est refusée plutôt que d’effacer le travail d’un autre constructeur. Un chantier interrompu au redémarrage doit être restauré avant d’être relancé.

## Limites du build

Un projet contient au plus 32 volumes et 350 000 positions suivies. Chaque segment fait 2 à 192 m, chaque largeur 3 à 64 m et chaque hauteur 3 à 32 m ; une station nécessite au moins 8 m de long et 13 m de large. Les points restent dans un rayon de 256 m autour de l’origine du projet.

Deux projets peuvent travailler simultanément. Les placements avancent par lots, avec un budget de temps par tick ; les aperçus sont calculés sur un autre thread et regroupés en grandes surfaces pour le rendu. Ces dispositions ne constituent pas une mesure de FPS sur une configuration utilisateur.
