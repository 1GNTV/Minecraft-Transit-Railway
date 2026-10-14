# SILLON PANAM PIXEL — Forge 1.20.1

Cette branche part de `work/sillon-panam-pids` (`1cb8bcf`, b176-r1). Le JAR conserve le mod ID `mtr` et remplace le JAR Sillon/MTR existant. Les moteurs HELIX/STRATA et les correctifs de signatures JCM sont conservés. La branche stable n'est pas modifiée.

## Géométrie

Le `facing` hérité des PIDS horizontaux MTR pointe **vers l'autre moitié**, pas vers le spectateur. Les propriétaires nord/est conservent les données ; l'autre moitié renvoie sa configuration au propriétaire. Le boîtier utilise deux moitiés ouvertes au raccord, sans montant central ni faces coplanaires internes. Le rendu est centré entre les deux positions et commence à Y=15,35/16. L'ouverture mesure 1,95 × 0,8875 bloc. Une seule image de fond compose chaque face, à 0,14 bloc du centre, devant le boîtier. Les sprites ont des profondeurs distinctes et sont découpés dans leur zone d'affichage.

Le même modèle couvre les quatre orientations existantes, sans migration du monde. Les panneaux orphelins ne rendent pas. La sélection et la configuration restent accessibles avec le pinceau MTR.

## Apparence et données

Le compositeur est une réimplémentation indépendante d'après le design et les mesures Vue/CSS du [PANAM d'arnoclr](https://github.com/arnoclr/panam), référence examinée à `main`. Aucun fichier Vue, police Parisine ni illustration originale de ce dépôt n'est redistribué. Les couleurs sont #0E0184, #FFBE00, blanc/noir et #F7F7F7. La grille suit le mode paysage : bandeau, chiffres à gauche, carte voyageurs à droite, horloge noire/jaune. Les textes sont rasterisés avec anticrénelage ; la police Minecraft n'est plus utilisée.

Les trains proviennent de `ArrivalsCacheClient`, triés par heure, identifiés par ligne/quai/indice de départ. Une correction d'ETA conserve l'identité. Les minutes sont réévaluées toutes les cinq secondes ; seules leurs modifications déclenchent le zoom. Le nombre de trains et les destinations déterminent le mode grands chiffres ou lignes de destinations. Les messages configurés dans MTR tournent toutes les dix secondes ; l'absence de source d'information voyageurs affiche « Informations en temps réel indisponibles ». Les horaires MTR prévisionnels sont identifiés comme tels. Les données de plus de 15 secondes sans réponse fraîche deviennent indisponibles ; les réponses d'une ancienne dimension sont rejetées.

Un seul départ dans la fenêtre MTR ne prouve pas que c'est le dernier train du service. Le dernier métro et l'interruption ne sont donc activés que par une indication explicite de l'exploitant dans les messages du pinceau : `[panam:last]` ou `[panam:suspended]`. Les autres messages restent des annonces locales. Aucun incident RATP ou flux réel externe n'est inventé.

Les zooms utilisent 2 s et cubic-bezier(.21,.92,.49,.97). Les entrées, sorties et déplacements utilisent 1,5 s et cubic-bezier(.38,-.17,.15,.92). Le panneau d'information utilise la rotation 10 s, les pictogrammes 1,6 s et le texte 1 s de la référence. Les clignotements des chiffres, points et deux-points sont distincts ; l'illustration d'interruption possède les deux traits progressifs sur 5 s.

## Police personnelle

Le JAR contient Noto Sans Regular (SIL OFL, licence embarquée), avec la Noto Sans Semibold déjà présente dans MTR. Cela change les métriques par rapport à Parisine : le rendu par défaut ne peut pas être qualifié de copie pixel pour pixel de la référence.

Un pack de ressources personnel peut fournir, si sa licence autorise cet usage :

```
assets/mtr/font/panam/parisine-regular.otf
assets/mtr/font/panam/parisine-bold.otf
```

Les deux fichiers sont chargés au rechargement des ressources (F3+T). Le mod ne les télécharge jamais. Ne distribuez pas ce pack sans les droits nécessaires.

## Architecture PIXEL

La scène fixe et ses sprites sont compilés par un worker borné, à partir d'une description immuable. Les glyphes, rangées, cartes et la conversion des pixels en image native sont préparés hors du thread de rendu ; les polices sont chargées une fois par génération. Les scènes identiques partagent leur atlas et les rangées identiques partagent leurs sprites. Les glyphes sont recadrés en conservant leurs positions et avances. Les surfaces changent lors des révisions MTR, de la configuration et du réseau ; une cadence d'une seconde entretient l'abonnement aux arrivées. La recherche automatique des quais est limitée à deux secondes et invalidée par le réseau/configuration.

Le GPU conserve les textures, sans réupload par image ni par changement de minute. Les transformations/alphas des petits sprites sont calculés sur CPU. Toutes les faces partagent le matériau texte natif, avec mélange alpha et écriture de profondeur. Le matériau de faisceau translucide triait les centres des grands/petits rectangles et cachait des sprites : les captures réelles ont permis d’écarter ce chemin. Aucun shader ou accès GL privé supplémentaire n'est requis : le chemin standard Forge/MTR est aussi le fallback Oculus. Un VBO persistant ou des uniforms dédiés n'ont pas été ajoutés à ces 10–15 rectangles : leur bénéfice n'a pas été établi, et ils demanderaient une validation Oculus distincte.

La résidence GPU est bornée à 64 Mio, avec expiration à 60 secondes ; une seule publication par tick ; au maximum huit travaux en attente et un worker. Les changements de ressources/dimension invalident les résultats en cours. Les commandes en attente sont annulées avant destruction des textures par leur gestionnaire ; les caches de matériaux sont également nettoyés. Les surfaces CPU sont libérées après publication. Les clés d'écrans sont faibles et le rendu est coupé à 64 blocs.

## Vérification et limites

`python tools/test_panam_pixel.py` exécute **les classes de production** du compositeur, des courbes et du plan de rectangles. Il sauvegarde les états normal, indisponible, multi-destinations, dernier métro, interruption, huit notices et plusieurs instants de transition dans `build/panam-verification/`. Il vérifie aussi les quatre coins de chaque face dans les deux orientations propriétaires. Les autres orientations sont leurs moitiés complémentaires.

Le rapport `pixel-profile.json` mesure le plan CPU après échauffement sur 100 000 itérations, la composition à froid et la taille de l'atlas. Exemple local : environ 0,35 µs par face, 10 rectangles, 4 Mio et 24 ms pour construire une nouvelle scène. Ces chiffres dépendent de la machine et du cas ; ils ne mesurent ni la soumission GPU, ni Minecraft, ni les FPS. La CI archive son propre rapport et ses images.

`python tools/test_panam_lifecycle.py` exécute le cache réel contre des frontières d'images/textures instrumentées : absence de publication GL sur le worker, conversion CPU hors rendu, déduplication, file bornée, échec d'upload, annulation/reload et fermeture unique. Ce test ne simule pas les performances d'un pilote graphique.

Les tests HELIX/STRATA, les vingt tests de géométrie existants, les contrats JCM sur le JAR final et la compilation Forge sont dans le workflow. Les images hors jeu valident l'apparence à l'aspect physique du boîtier ; la référence utilisateur possède un autre rapport largeur/hauteur. Une comparaison exacte exige les mêmes dimensions et une police autorisée.

Un client officiel Forge 47.4.28 / Minecraft 1.20.1 a également chargé le JAR local et un monde superflat isolé avec les quatre moitiés PANAM. Les captures du framebuffer réel vérifient le boîtier continu, les deux faces, le texte et l’horloge, en vue frontale et oblique, sans panneau détaché ni stries constatées. Cette vérification ne comprend pas le pack complet ni une ligne réelle. Les scripts `prepare_pixel_lab.py` et `prepare_pixel_runtime.py` préparent uniquement ce laboratoire temporaire.

Restent à vérifier dans le jeu avec le JAR distribué : pose/casse sur limites de chunks, vues obliques et profondeur réelle, F3+T, chargement/déchargement, arrivées d'une vraie ligne, Oculus 1.8/shaders, JCM 2.1.2 et MTR France 1.2.0 dans le pack complet. La compilation et les contrats binaires ne prouvent pas cette compatibilité en exécution. Aucun gain FPS n'est annoncé.
