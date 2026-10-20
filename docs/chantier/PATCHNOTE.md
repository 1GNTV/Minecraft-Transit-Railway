# Sillon Chantier — Forge 1.20.1

## Construction dans le monde

- Nouvel outil Chantier : deux clics pour tracer une gare, un tunnel, une salle ou un escalier.
- Aperçu visible à travers le terrain ; poignées pour déplacer les extrémités et ajuster largeur/hauteur.
- Molette pour les dimensions, Alt pour le niveau, G pour les quatre onglets de réglages.
- Visite temporaire en spectateur pour examiner le projet souterrain, avec retour au point de départ.
- Accès latéraux, mezzanines avec escalier et quatre amorces de tunnel depuis une station.
- Tunnels suivant une voie MTR existante, y compris ses courbes et pentes.

## Architecture et terrain

- Fusion des volumes en contact, avec enveloppe continue, quais latéraux ou centraux.
- Plafonds plats, voûtés ou arrondis ; matériaux, éclairage et piliers réglables.
- Enveloppe posée avant excavation ; prise en charge des pierres, minerais, cavités et liquides.
- Analyse avant placement ; refus des coffres, blocs techniques et constructions protégées.
- Travaux répartis sur plusieurs ticks, arrêt et restauration de l’opération en cours.

## Modifications et sauvegarde

- Mise à jour du projet par différences ; les parties retirées retrouvent leur terrain initial.
- Brouillons par monde/serveur et dimension, plusieurs projets, sauvegarde à Échap.
- Annulation/rétablissement des réglages du brouillon.
- Journal compressé côté serveur pour restaurer un projet entier, y compris après fermeture du jeu.
- Création de vraies gares, voies de quai et voies ordinaires MTR ; conservation des voies existantes.
- Refus des modifications destructrices de quais déjà desservis ou raccordés à des voies extérieures.

## Installation et périmètre

Forge 1.20.1, même JAR côté client et serveur. Outil réservé aux opérateurs en créatif : `/give @s mtr:chantier`.
Les lignes, horaires et dépôts se configurent dans le dashboard MTR. Un projet est limité à 32 volumes et 350 000 positions suivies. Les éclairages utilisent la lanterne aquatique, les escaliers le quartz lisse. Voir [la prise en main](PRISE-EN-MAIN.md).
