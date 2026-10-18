# Utiliser la signalétique

1. Placer le bloc **Signalétique Sillon**, puis l'ouvrir avec la brosse MTR.
2. Dans **Modèles**, choisir un exemple dans la galerie ; la recherche accepte aussi les noms sans accents.
3. **Texte** règle les deux lignes du nom, le sous-titre, l'automatisation MTR et la flèche générale.
4. **Lignes** permet d'ajouter des destinations indépendantes, de les supprimer et de changer leur ordre. Les anciens textes sont repris comme lignes.
5. **Icônes** affiche la bibliothèque réelle. Choisir la cible **Panneau** ou **Ligne**. Cliquer pour ajouter/retirer ; **Sélection** permet de réorganiser les éléments avec les flèches.
6. **Aspect** règle la famille, la police, les couleurs hexadécimales, les tailles, les marges et le cadre.
7. **Pose** règle les dimensions en blocs, la fixation, les décalages, les trois angles, l'épaisseur, les tiges, le rétroéclairage et les deux faces.
8. **Enregistrer** envoie le document au serveur. **Annuler** ou Échap ferme sans appliquer les modifications.

L'aperçu conserve les proportions et reste visible aux tailles d'interface testées. Utiliser les boutons de page ou la molette pour accéder aux champs supplémentaires.

**Copier / Coller** reprend le panneau complet, y compris sa pose. **Annuler / Rétablir** en haut de l'écran concerne l'historique d'édition.

## Données MTR

Le nom automatique concerne les plaques de station. Pour un plan de ligne ou une direction de quai, l'automatisation permet d'utiliser le quai MTR proche si aucune liste manuelle n'a été saisie. Les départs numériques proviennent du quai voisin et ne sont pas des horaires inventés.

## Image personnalisée

Placer une image PNG dans un pack de ressources, puis saisir son identifiant dans **Aspect > Image du pack de ressources**, par exemple :

mtr:signage/maps/mon_plan.png

Le fichier se situe alors dans assets/mtr/signage/maps/mon_plan.png. Il est affiché sans étirement. Les modèles **Plan / image** et **Composition libre** acceptent cette image.

## Compatibilité des mondes

Les documents de l'ancien format sont lus. Le nouveau format conserve les réglages avancés et les lignes séparées. Utiliser le même nouveau build côté client et côté serveur pour les modifications réseau.
