# Prochain build Sillon — Forge 1.20.1

## Composition des rames

Le catalogue occupe la largeur de la fenêtre, la composition reste en bas.
Les profils utilisent les textures du modèle MTR, à proportions conservées.
Maj + clic sélectionne une plage ; Ctrl + clic ajoute ou retire une voiture.
Glisser la sélection réordonne les voitures ensemble. La zone rouge en haut les supprime.
Ctrl + Z / Ctrl + Y rétablissent les modifications du brouillon ; Ctrl + D duplique la sélection.
Échap valide et ferme si la rame tient dans la voie de garage. Annuler abandonne le brouillon.

## Contours des gares et dépôts

Éditer une zone dans le dashboard, puis choisir « Tracer le contour… ».
Cliquer pour placer les sommets et cliquer sur le premier pour fermer.
Glisser un sommet pour le déplacer ; clic droit pour le retirer ; Maj + clic sur un côté pour en ajouter un.
Molette pour zoomer, bouton central pour déplacer la carte.
Le bouton Rectangle conserve le mode habituel.
Un contour qui se croise ne peut pas être enregistré.

Les contours modifient réellement le rattachement MTR des quais et voies de garage.
Ils sont enregistrés et synchronisés avec les données natives. Les anciennes zones restent rectangulaires.

## Cheminements des correspondances

Placer des « Points de cheminement au brush » aux tournants et paliers du trajet réel.
Ils sont visibles uniquement avec le brush et ne constituent pas un obstacle.
Maj + clic au brush sur deux points les relie dans les deux sens.
Le clic au brush ouvre l’éditeur : nom, passage/quai/sortie/escalier/ascenseur, quai MTR associé et liaisons à retirer.

Dans l’éditeur d’un panneau automatique, activer « Suivre les cheminements au brush ».
Associer le quai ou la ligne à rejoindre. Le panneau utilise le premier segment du chemin déclaré.
Les sorties et services peuvent suivre les points correspondants du graphe.
Sans chemin renseigné, le panneau affiche « Cheminement à définir ».
Les liaisons sont conservées par dimension, y compris quand leurs blocs sont dans des chunks déchargés.
La destruction d’un point retire ses liaisons. L’éditeur attend l’accusé du serveur avant de fermer.

Les points doivent suivre les couloirs construits : le système ne découvre pas automatiquement les murs et escaliers.

## Borne d’itinéraire

La borne existe sur pied, murale, en double face et en version basse.
Un clic ouvre la recherche ; avec le brush, il ouvre la personnalisation du mobilier.
Choisir le départ et l’arrivée. Le moteur MTR calcule l’itinéraire avec ses horaires :
lignes, directions, quais, étapes à pied, correspondances et heures disponibles.
L’absence d’un départ ou d’un itinéraire est affichée explicitement.

## Mobilier et performances

Les collisions suivent les éléments du modèle et leur rotation, même au-delà du bloc d’ancrage.
Les espaces sous les sièges et dans les voûtes restent libres.
Les normales et groupes de matériaux sont préparés une fois et conservés tant que l’objet est chargé.
Le rendu peut éliminer les objets hors champ à partir de leurs dimensions complètes.
La détection des services voisins est limitée globalement à 192 blocs par tick, hors du rendu des panneaux.

Ces réductions de travail ne permettent pas de promettre un nombre de FPS sur une autre machine.
Le résultat doit aussi être comparé sur la gare et la configuration graphique utilisées par le joueur.

## Sauvegarde et compatibilité

Échap sauvegarde les valeurs valides dans les éditeurs de rame, voie, signalétique, mobilier, PANAM et cheminement.
Annuler reste une action explicite d’abandon. Les erreurs de saisie empêchent la fermeture.
Les opérations de groupe et de répétition du mobilier nécessitent toujours leur bouton dédié :
Échap sauvegarde seulement les réglages de l’objet d’origine.

Les nouveaux paquets sont ajoutés après les anciens. Les identifiants des anciennes familles de mobilier restent en place.
Les vérifications du build couvrent les descripteurs attendus par JCM / MTR France.
Utiliser le même nouveau JAR Sillon sur le client et le serveur.
