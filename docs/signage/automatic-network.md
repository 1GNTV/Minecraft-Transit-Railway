# Signalétique liée au réseau MTR

Les 23 modèles décrivent une apparence. Aucun modèle ne contient une ligne parisienne, une liste d'arrêts ou une adresse imposée. Les 18 familles utilisent le même résolveur.

Le document sauvegarde les identifiants MTR de la station, du quai et de la desserte, ainsi que les réglages de composition. La valeur « Détection automatique » utilise la station contenant le panneau et le quai proche. L'onglet **Réseau** permet de lever une ambiguïté (quais superposés, sens, branches), sans recopier les arrêts.

Un abonnement client partagé récupère les données complètes avec l'opération MTR `LIST_DATA`, également utilisée par le tableau de bord. Il est renouvelé au plus une fois toutes les dix secondes. Les textures dépendent des données résolues : renommer une station, modifier la couleur ou le numéro d'une desserte, ou changer ses arrêts actualise les panneaux associés.

| Familles | Sources utilisées |
| --- | --- |
| Plaques métro, RER, Grand Paris | Nom et sous-titre de station |
| Directions, correspondances | Quais, destinations, dessertes et stations connectées |
| Plans de ligne, quais | Ordre réel des arrêts, correspondances à chaque arrêt, couleur et numéro de desserte |
| Plan du réseau | Toutes les lignes reçues et coordonnées des stations du monde |
| Sorties, secteur | Numéros et destinations des sorties renseignées dans MTR |
| Colonnes, totems | Mode, numéro et couleur de la desserte sélectionnée |
| Services | Étages d'ascenseurs MTR ; escalators, escaliers et billetterie détectés dans les blocs chargés proches |
| Départs | Cache d'arrivées MTR du quai sélectionné, destination et temps d'attente |
| Panneaux composés, libres | Destinations, services et sorties issus des mêmes données |

Les pastilles sont composées avec le libellé et la couleur réels : une ligne personnalisée ne reçoit jamais la couleur parisienne d'un numéro semblable. Les modes non distingués par MTR peuvent être précisés dans Réseau.

## Références visuelles

Les photos fournies pour cette correction ont guidé les totems (grand symbole de mode, indice, flèche basse), les listes de sorties (bandeau, numéros encadrés, lignes alignées en haut), le plan horizontal (noms inclinés, terminus en cartouche, correspondances sous le tracé) et le plan vertical (correspondances à gauche, séparateurs et noms à droite).

Références publiques : [prescriptions signalétiques IDFM](https://prim.iledefrance-mobilites.fr/fr/chartes-et-prescriptions/prescriptions-signaletiques), [prescriptions cartographiques IDFM](https://prim.iledefrance-mobilites.fr/fr/chartes-et-prescriptions/prescriptions/prescriptions-operationnelles/prescriptions-cartographiques). Les ressources, polices et provenances déjà intégrées restent documentées dans `assets-provenance.json`.

## Limites des données

MTR ne fournit pas de position pour ses sorties textuelles ni de graphe piéton des couloirs. Leurs flèches restent réglables. Les autres flèches automatiques donnent une orientation géométrique vers le quai ou service, relative au panneau ; elles ne calculent pas un trajet contournant les murs. Un nom de secteur interne absent de MTR ne peut pas être déduit : le panneau utilise le nom de station. Les services distants ou non enregistrés ne sont pas inventés.

Le mode manuel permet une création libre ; pour un plan de ligne, son activation reprend les arrêts générés comme lignes éditables. Les anciens panneaux manuels restent modifiables. Pour les relier au réseau, activer l'automatisation ou appliquer un nouveau modèle.

## Vérifications reproductibles

- `python tools/test_sillon_signage_reference.py` : 18 familles, 23 rendus, plan dense de 21 arrêts dans les deux orientations, 502 pictogrammes, polices, sérialisation v3, édition et géométrie de l'interface.
- `SignageMtrIntegration.java` : véritables stations/quais/rails/routes/ascenseurs du cœur MTR, sérialisation `LIST_DATA`, lecture par l'adaptateur de production, correspondances et modifications en conservant les identifiants.
- Workflow Forge : compilation du JAR, vérification des ressources livrées et des descripteurs JCM, puis essais natifs séparés. Un rendu Java du compositeur ne constitue pas une capture de Minecraft ; les captures natives et les résultats de shaders doivent être vérifiés séparément.
