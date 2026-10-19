# Revue de fidélité — 19 octobre 2026

Les images sont produites par le compositeur Java réellement utilisé en jeu. Elles ne sont pas des maquettes générées par IA. Les scènes parisiennes servent uniquement de fixtures de comparaison ; les modèles livrés récupèrent les noms, indices, couleurs, arrêts et correspondances depuis MTR.

## Images ouvrables directement

- [Colonne métro : photo et texture corrigée](totem-corrige.jpg)
- [Toutes les familles](tous-les-panneaux.jpg)
- [Écran PANAM](ecran-panam.jpg)
- [Plan vertical](plan-vertical.jpg)

La colonne reproduit désormais la découpe volontaire à droite du M et du disque de ligne, le bandeau bleu supérieur, la flèche anguleuse et une face bombée. Les colonnes murales et les supports au sol ont des fixations distinctes.

## Sources vérifiées

- [Prescriptions signalétiques IDFM V3.1 et bibliothèques officielles](https://prim.iledefrance-mobilites.fr/fr/chartes-et-prescriptions/prescriptions-signaletiques), page mise à jour le 10 juin 2026. Construction des indices, flèches, hiérarchie des supports, fontes et pictogrammes. Voir les pages 34, 42–44 et 88–94 du PDF.
- [RATP DesignLab](https://askip.ratpgroup.com/a-la-decouverte-du-designlab-de-la-ratp/), Parisine, patrimoine des plaques et écrans PANAM.
- [AREP — signalétique du Grand Paris Express](https://www.arep.fr/nos-projets/signaletique-du-grand-paris-express/), photographies de Kévin Laclautre. Directions sur fond blanc, texte noir, flèche dans un compartiment séparé.
- [Typotheque — Typeface for Paris Metro](https://www.typotheque.com/articles/typeface-for-paris-metro), police Grand Paris et photos des supports.
- [Photo de Châtelet — Chabe01, 7 mai 2023](https://commons.wikimedia.org/wiki/File:Panneaux_Direction_Lignes_Métro_Secteur_Rivoli_Station_Châtelet_Métro_Paris_-_Paris_I_(FR75)_-_2023-05-07_-_1.jpg). Référence des colonnes et du secteur Rivoli.
- [PANAM à Porte Maillot — Remontees, 8 mai 2024](https://commons.wikimedia.org/wiki/File:Écran_SIEL_Métro_Paris_Ligne_1_Porte_Maillot.jpg). Disposition de l’écran, horloge, deux attentes et zone d’information.
- [Plan officiel de la ligne 14](https://www.ratp.fr/sites/default/files/lines-assets/plan-de-ligne/metro/plan-de-ligne_metro_ligne-14.1740960332.pdf), lien fourni par la [page RATP de la ligne](https://www.ratp.fr/vos-lignes/metro/14). Vérification des 21 arrêts et des correspondances de la fixture.

## Contrôle des 18 familles

| Famille | Révision | Données en mode automatique |
| --- | --- | --- |
| Plaque métro | Parisine, boîte d’encre, proportions | Nom de la station |
| Plaque avec sous-titre | Bandeau indépendant, nom sur deux lignes | Nom et description MTR |
| Plaque RER | Secteur facultatif, symbole et signature réglables | Nom et description MTR |
| Direction | Colonnes de symboles, flèche commune, retour à la ligne | Ligne et destination desservie |
| Correspondances | Groupes de modes et d’indices, pas de faux anneaux RER | Lignes réellement présentes |
| Panneau composé | Transport/service clair, sorties foncées | Lignes, services et sorties réels |
| Sorties | Grands numéros encadrés, rythme régulier | Sorties de la station MTR |
| Secteur | Symbole séparé du nom, bandeau, lignes, sorties | Sorties MTR ; secteur choisi dans l’éditeur |
| Colonne métro | Bandeau bleu, symboles tronqués à droite, surface bombée | Mode et indice du quai |
| Totem métro | Même composition, support au sol distinct | Mode et indice du quai |
| Desserte de quai | Noms inclinés, arrêts et correspondances | Arrêts restants depuis le quai |
| Plan de ligne | Disposition horizontale/verticale, hauteur adaptable | Parcours complet ou restant |
| Plan du réseau | Graphe commun aux lignes, légende, évitement des libellés | Stations, positions et connexions MTR |
| Services | Pictogrammes officiels, traductions facultatives | Ascenseurs et équipements détectés |
| Départs | Structure PANAM, horloge, deux attentes, message réglable | Prochains départs du quai associé |
| Composition libre | Lignes indépendantes et image de pack facultative | Composition générée depuis la station |
| Nom Grand Paris | Police Grand Paris, fond blanc, texte noir | Nom de la station |
| Direction Grand Paris | Compartiment flèche distinct | Destination de la desserte |

## Vérification et limites

`tools/test_sillon_signage_reference.py` compile les classes réelles, vérifie la sérialisation v4 et la migration v3, les réglages de l’éditeur, les modifications d’un réseau arbitraire pour les 18 familles, les indices RER/tramway, le bandeau supérieur des colonnes et le découpage du M. Il produit 23 rendus de modèles et 20 fixtures de revue. Ces tests ne constituent pas une comparaison photographique automatique.

Le plan de réseau est un schéma calculé à partir du monde MTR : il ne prétend pas reproduire la géographie parisienne. Le nom du secteur, sa convention graphique et les messages voyageurs restent des choix explicites, car MTR ne fournit pas ces informations. Les flèches automatiques indiquent la position de la cible ; elles ne calculent pas un itinéraire piéton dans les couloirs. Une flèche manuelle reste disponible.

La compilation Forge, l’intégration avec les objets MTR réels et les captures natives de l’éditeur sont exécutées dans le workflow `signage-refonte.yml`. Le statut du workflow est la référence pour ces vérifications ; les textures ci-dessus ne sont pas présentées comme des captures Minecraft.

## Crédits

La partie photographique et le recadrage de `totem-corrige.jpg` : Chabe01, Wikimedia Commons, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Photo réduite et recadrée, sans modification du panneau photographié. La planche comparative est proposée sous CC BY-SA 4.0 ; cette mention ne change pas la licence du code.

Les SVG officiels importés sont conservés dans `official-svg/`, avec le chemin de chaque objet et son empreinte dans `official-objects.json`. Les fontes et objets IDFM conservent leurs mentions d’origine dans le JAR.
