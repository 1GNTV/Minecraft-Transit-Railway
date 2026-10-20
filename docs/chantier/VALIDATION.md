# Validation du build Sillon Chantier

Source testée : `fab00aeb2b1da5859af1a6981388f3ffdcc20d30`.
[Exécution complète réussie](https://github.com/1GNTV/Minecraft-Transit-Railway/actions/runs/39514451083).

JAR : 102 272 403 octets.
SHA-256 : `bad1be5f4c5ddd3980353798dbb330d684ca9f12ac3e6e265878547a1d0e2d63`.

## Contrôles réussis

- Compilation Forge 1.20.1 ; vérification du JAR, ressources et signatures utilisées par JCM.
- Régressions des systèmes précédents : signalétique, mobilier, compositeur et ordre des paquets.
- Géométrie : trois profils, pentes, orientations, enveloppes fermées, jonctions, quais, mezzanine et escaliers ; même résultat par petits lots ; surfaces GPU et annulation des calculs.
- Véritable cœur MTR : gare, quais, voies ordinaires, nœuds, associations, ajustement sans doublons, conservation du nom personnalisé, absence de voies dupliquées en suivi de voie, suppression des données créées.
- Serveur Minecraft réel pour préparer le monde ; client réel pour les paquets de construction et les contrôles suivants.
- Construction à travers andésite, minerai de diamant et eau ; intérieur sec, plancher de mezzanine présent, journal compressé effectivement enregistré.
- Coffre protégé : refus avant modification du terrain.
- Redimensionnement et retrait d’un accès : restauration de son terrain d’origine.
- Restauration complète du projet et annulation après de vraies mutations : comparaison exacte avec tous les états initiaux suivis.
- Aperçu visible dans le monde et couleurs des poignées contrôlées sur les pixels rendus ; déplacement dans une même cellule sans décalage du point.
- Visite en spectateur, terrain inchangé, retour au créatif et à la position de départ.
- Quatre onglets sans chevauchement de widgets en 1120 × 720 et 640 × 480 ; fermeture avec conservation du brouillon.
- Même scénario complet avec Oculus 1.8.0, Embeddium 0.3.31 et Complementary Reimagined r5.3 ; présence du pack et du pipeline de shader vérifiée.

## Portée

Le rendu CI utilise Mesa llvmpipe, pas une carte graphique utilisateur : ces essais ne mesurent pas les FPS matériels. Ils ne constituent pas un test de tous les modpacks. Les journaux sont relus sur disque pendant les ajustements et restaurations ; un arrêt brutal du processus n’a pas été injecté dans ce scénario.

Les limites du générateur et les réglages MTR encore manuels sont décrits dans [la prise en main](PRISE-EN-MAIN.md). Les captures de cette exécution sont dans [le dossier de revue](../signage/review/native/).
