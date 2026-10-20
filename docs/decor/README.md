# Sillon — kit de gare

20 blocs originaux, chacun avec quatre variantes, géométrie réglable et éditeur au brush MTR. Forge 1.20.1 est la cible de validation. Les textures sont créées pour Sillon, sans reprendre les modèles de MTR France.

## Contenu

Voûte, habillage de pilier, parement nervuré, panneau acoustique, plafond à lames, corniche/plinthe, bandeau lumineux, suspension, applique, banc, appui debout, tri sélectif, borne d'appel, interphone, coffret extincteur, armoire technique, ventilation, main courante, garde-corps et cadre d'affiche.

Les dimensions sont exprimées en blocs. Les nervures et les lames sont répétées et non étirées. Les matériaux se répètent à échelle constante. Le banc possède ses pieds, ses traverses, ses lattes et ses accoudoirs ; les rampes sont des tubes à douze côtés. Les surfaces émissives utilisent le contexte de rendu des blocs Minecraft.

## Utilisation

1. Chercher « Sillon » dans les blocs de construction de gare MTR et poser un élément.
2. Clic droit avec le brush : Dimensions, Finition, Pose et Groupe. Les champs sont paginés pour rester utilisables à petite résolution.
3. Le rendu de droite utilise les mêmes faces et matériaux que le modèle en jeu. Modifier une valeur ne change le monde qu'après Enregistrer.
4. Groupe → Choisir les deux coins : Maj + clic sur chacun des deux blocs qui bornent la zone. Rouvrir le panneau Groupe et cocher les propriétés à appliquer. Seule la famille de l'objet source est modifiée.
5. Groupe → Répéter : choisir l'axe du monde, le nombre et l'espacement entier des ancres. « Voir la pose en jeu » affiche les copies translucides tant que le brush est tenu. Rouvrir l'objet source et valider la pose.
6. Groupe → Aligner : alignement X, Y ou Z de la même famille par décalage visuel de l'ancre. Déplacement maximal de huit blocs.
7. Annuler la dernière opération restaure l'ensemble de la dernière modification, si aucun objet n'a été changé entre-temps. Historique limité à douze opérations et dix minutes, dans la même dimension.

Le mode groupe exige les droits opérateur. Les zones sont limitées à 48 blocs par axe, 32 768 blocs de volume et 256 objets par opération. La répétition s'arrête à 48 blocs du modèle et refuse toute l'opération si un emplacement est occupé ou non chargé. Aucun chunk distant n'est chargé pour ces opérations.

## Références de conception

Ces objets sont des modèles originaux de mobilier et d'architecture ferroviaire, pas des répliques certifiées d'un fabricant ou d'une gare particulière. Les dimensions sont des valeurs de conception adaptées à Minecraft, pas des mesures relevées sur plans.

- [Patrick Jouin iD — Grand Paris Express](https://www.patrickjouin.com/fr/projets/le-grand-paris-express-1452.html) : cohérence du mobilier, supports et équipements, photographies de gares ouvertes.
- [Société des grands projets — équipements](https://www.grandparisexpress.fr/equipements-grand-paris-express) : familles d'équipements et intégration dans l'architecture.
- [Hunter Douglas — Derako linéaire](https://www.hunterdouglasarchitectural.eu/fr-fr/plafonds-fr-2/plafonds-interieurs-en-bois/lineaire-bois/derako-lineaire-baseline-bois-massif/) : principe de lames répétées sur porteurs, pour plafonds et parements.
- [Hunter Douglas — HeartFelt Groove](https://www.hunterdouglasarchitectural.eu/fr-fr/plafonds-fr-2/plafonds-feutre-interieur/panneaux/heartfelt-groove/) : répétition des modules et intervalles réguliers.
- [Transilien — bornes d'appel](https://www.transilien.com/fr/page-medias/BAUI) : séparation information/urgence et implantation sur les quais.
- [EPSILON — mobilier urbain](https://www.epsilon-tolerie.fr/secteur-urbain/mobilier-urbain) : familles bancs, appuis, corbeilles et mobilier en tôlerie.
- [EPSILON — secteur urbain](https://www.epsilon-tolerie.fr/secteur-urbain) : armoires de quai et équipements métalliques.

Aucune photographie de ces sites n'est incorporée au JAR. L'affiche par défaut est une composition originale Sillon ; un resource pack peut la remplacer en renseignant `namespace:textures/chemin.png`.

## Limites explicites

- Éléments décoratifs sans collision étendue : les murs, voûtes, garde-corps et armoires ne remplacent pas la structure physique de la construction.
- Bancs visuels, sans interaction pour s'asseoir ; bornes et interphones sans appel simulé ; extincteurs et armoires sans inventaire.
- Les modèles sont rendus par ancres de blocs, avec un cache de géométrie borné. Ils ne sont pas encore fusionnés dans les meshes de chunks ; aucune promesse de performance sur une gare de plusieurs milliers d'objets.
- Pas de raccord automatique entre deux voûtes sécantes, ni de courbe libre. Les angles sont des variantes explicitement choisies.
- La modification groupée de ce kit concerne ses vingt familles, pas les objets de MTR France.

## Vérification et rendus

`python tools/test_station_decor.py` compile et exerce les classes de production : 80 variantes, 80 géométries extrêmes, sérialisation NBT et réseau, préservation des propriétés non cochées et bornes de répétition. Les fiches et les OBJ sont exportés directement de ces mêmes meshes dans `build/decor-review/`.

Les captures natives sont produites par un mod QA distinct du JAR utilisateur : vingt vues d'objets, huit vues de l'éditeur à deux résolutions, modification via le vrai paquet brush, synchronisation, répétition et annulation. La même scène est exercée avec Oculus, Embeddium et Complementary. Les fiches isolées sont des rendus logiciels de la géométrie, pas des captures Minecraft.
