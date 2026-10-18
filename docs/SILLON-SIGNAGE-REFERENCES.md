# Signalétique Sillon : sources et règles visuelles

## Sources examinées

| Famille | Référence |
|---|---|
| Plaques métro et typographie Parisine | [RATP : la typographie du métro](https://www.ratp.fr/decouvrir/patrimoine/la-typographie-du-metro) |
| Transport sur fond blanc, sorties bleues, services particuliers gris | [RATP : Fiches équipements, page Signalétique](https://www.ratp.fr/sites/default/files/inline-files/fiches_equipements_1.pdf) |
| Châtelet, secteurs et fabrication des panneaux | [Girod : réaménagement Châtelet-Les Halles](https://girod-group.com/portfolio/signaletique-email-chatelet-les-halles/) |
| Plaque RER Châtelet-Les Halles, secteur Forum | [Photographie originale et crédits Commons](https://commons.wikimedia.org/wiki/File:Panneau_Gare_Ch%C3%A2telet_Halles_RER_Paris_Ligne_B_-_Paris_I_(FR75)_-_2022-07-05_-_2.jpg) |
| Secteur Rivoli | [Photographie de l'accès Rivoli](https://chateletleshalles.com/sortie11/) |
| Grand Paris, Villejuif-Gustave Roussy | [AREP : signalétique du Grand Paris Express](https://www.arep.fr/nos-projets/signaletique-du-grand-paris-express/) |
| Indices de lignes, marges, flèches, couleurs et polices actuelles | [IDFM : prescriptions signalétiques V3.1](https://prim.iledefrance-mobilites.fr/fr/chartes-et-prescriptions/prescriptions-signaletiques) |
| Ordre et noms des stations de la ligne 14 | [Plan officiel RATP](https://www.ratp.fr/en/plans-lignes/metro/14) |
| Plan régional intégré | [IDFM : plan du réseau 2026](https://eu.ftp.opendatasoft.com/stif/PlansRegion/Plans/PLAN_POCHE_RESEAU.pdf) |
| Pictogrammes de MTR France | [Source épinglée eaf2a813](https://github.com/1GNTV/MTR-France-Addon/tree/eaf2a8130ac31f12534480a567c9246f35c0463e/src/main/resources/assets/mtr/mtr_france_sign) |

Les manifestes dans docs/signage conservent les URL, les empreintes des ressources et les éventuels échecs de téléchargement. Les planches pertinentes du référentiel ont été rendues pour examen, notamment les pages 16-17 (polices), 22-28 (couleurs), 35-38 (indices), 85 (flèches) et 88-95 (composition).

## Ce que le rendu applique

- Parisine pour les familles RATP classiques ; Grand Paris pour les modèles GPE ; IDF Voyageur disponible séparément. Les fichiers officiels restent inchangés.
- Les silhouettes des pictogrammes proviennent des ressources d'origine. Leur rapport largeur/hauteur est conservé.
- Le catalogue embarque 497 images d'origine et 5 variantes dérivées identifiées : quatre quadrants du tableau d'assistance et le logo RATP blanc.
- Une plaque de station, un jalonnement blanc, une liste de sorties bleue et un panneau de secteur ont des compositions distinctes.
- Les éléments de secteur déjà légendés ne reçoivent pas une seconde légende identique.
- Toutes les couleurs saisies sont utilisées ; aucune famille ne remplace silencieusement la palette choisie.
- Les flèches disposent de huit orientations ; les lignes composées peuvent avoir des flèches et des pictogrammes indépendants.
- Les marges, l'échelle typographique, l'échelle des pictogrammes, l'alignement et le bandeau sont réglables.
- Le plan intégré est le plan réel IDFM, pas une topologie inventée. Une image de pack peut le remplacer.
- Les dessertes automatiques utilisent les données MTR du quai voisin ; les lignes manuelles restent éditables.
- Les deux faces utilisent le même pivot physique et l'épaisseur choisie, sans séparation artificielle d'un bloc.
- Les cadres, caissons, tiges de suspension et pieds sont géométriques et suivent la pose du panneau.

## Limites de fidélité

Les fichiers graphiques et les polices sont vérifiés à leur source. Les couleurs historiques de plaques RATP et les dimensions des modèles restent des estimations photographiques, éditables dans le jeu : les photos ne constituent pas des plans de fabrication. Le système n'est pas une reproduction certifiée par un opérateur. Les modèles de composition et les contenus d'exemple sont distingués des photographies exactes.

## Contrôles reproductibles

- tools/test_sillon_signage_reference.py compile et exécute le vrai compositeur Java : 18 familles, 22 modèles, toutes les images, les trois polices et les règles d'édition.
- Le même contrôle vérifie la sérialisation des documents et des lignes Unicode, les limites du paquet réseau, les valeurs invalides et la géométrie responsive.
- Le workflow Forge compile le code contre Minecraft 1.20.1 et vérifie les descripteurs de compatibilité JCM.
- Les outils dans tools/qa-runtime constituent un mod de test séparé. Celui-ci charge le JAR utilisateur assemblé, après remappage Forge de développement, pour les captures et le contrôle réseau. Les classes de test ne sont pas ajoutées au JAR utilisateur.
- Le résultat des tests natifs et les captures réellement obtenues sont conservés dans docs/signage/review/native. Une compilation seule ne vaut pas validation visuelle ou validation sous shaders.
