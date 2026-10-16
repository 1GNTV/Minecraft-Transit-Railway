# PANAM 1:1 — Sillon SOLIS (Forge 1.20.1 + Fabric 1.20.4)

Status: source integrated on **both branches**; references are the user's real photos of RATP line 10 and line 12 double-sided suspended platform displays, from front, underneath and side. These source changes do not assert pixel-perfect physical photogrammetry. Runtime visual inspection is required.

## 3D geometry
- Exactly **two blocks wide**, preserving MTR's existing horizontal twin-block state and platform/arrival configuration.
- The screen is **13.15 model pixels high** (was 11.75), with its upper edge at 12.75 model pixels. After the signed pitch/depth transform its lower edge is about 0.51 px below block baseline. Pixel layout stays 1024 x 466, preserving the 2 ETA slots, central separator, upper white destination strip and information panel.
- The render transform applies a **2.6° downward-facing pitch**. Both upper edges are farther apart than the corresponding lower edges.
- The block-model silhouette now contains an independent central black housing/spine, paired stepped-profile thin display cartridges, pale upper caps, dark middle cheeks and a route-colored projecting lower lip and **one narrow ceiling suspension rod per block**. The repeated two-block placement creates two rods spread over the full width, not one fat centre pole. Fine recessed casing slices approximate the screen tilt without introducing invalid non-22.5° Minecraft JSON model element rotations.
- The line-coloured horizontal strip is still drawn from the actual MTR route colour by the existing PanamSurface GPU texture. The physical lip and lower side returns use the same route color through a block-color provider. Both half-block meshes refresh only when this color changes; no fixed line 12 green remains.
- Existing line/mixed-departure/clock/pictogram visuals remain; the panel is not globally scaled arbitrarily and the old MTR block ID remains unchanged.

## Traffic bulletin timeline
- **Important DANGER or STOP bulletin for the currently displayed route**: 12 seconds per display, interleaved with noncritical network bulletins rather than monopolising the display.
- **Ordinary line notice and planned works**: 6.5 seconds, no critical priority.
- **General network/other-line bulletin**: 4.5 seconds.
- If a critical event exists alongside ordinary bulletins, the order is **critical / next network bulletin / critical / next network bulletin**, advancing the ordinary-bulletin cursor fairly.
- With no critical event, rotate all messages in order. Display clocks advance independently. Changing arrivals alone does not reset the bulletin cycle.
- Every event is still drawn by the existing independent right-hand column while the left-side train ETAs continue to update.
- Current-route critical alerts can displace one lower-priority bulletin if the list is full, avoiding lost hazard reports. Global network notices are still included.
- Only actual MTR/PN2 operator data is shown; **do not fabricate real-world alerts**.

## Validation and rollback
- Test `python3 tools/test_panam_pixel.py` (real compositing oracle + timeline + 3D silhouette invariants), `test_panam_camera_order.py`, `test_panam_eta_contrast.py`, `test_panam_arrival_parity.py` and `test_panam_lifecycle.py`.
- Compile and validate a fresh **Fabric 1.20.4** jar. Forge 1.20.1 receives exactly the same changes in its shared `fabric/src/main/java` and assets tree, but is not compiled unless requested.
- Check in-game front and underside screenshots, line 10/12 colour strip, Oculus, JCM/MTR France, shader placement, billboard overlay depth, and actual rendering at all four facing directions.
- Preserve prior SOLIS commits as rollback points. This pass changes no core MTR/JCM ABI.

## PANAM écran seul (Fabric 1.20.4 / Forge 1.20.1)

- Nouvel item `mtr:panam_screen`, dans les installations ferroviaires MTR.
  Un seul bloc d’ancrage sélectionnable, sans boîtier, avec une seule face visible.
  La face regarde le joueur à la pose. L’arrière n’affiche rien, même sur une
  couche de rendu sans culling. L’ancrage reste à sa place après un décalage.
- Brush MTR sur l’ancrage → **Affichage**. Échap enregistre côté serveur :
  position XYZ en blocs (−16 à +16), rotation Y/X/Z en degrés, largeur/hauteur
  (0,25 à 16 blocs), résolution L/H (128 à 2048 pixels), zoom (0,5 à 3).
  **Réinitialiser** restaure uniquement les réglages d’affichage.
- Les réglages sont persistés en NBT, synchronisés par MTR et bornés côté serveur.
  Les modifications exigent le brush en main et un ancrage chargé à moins de
  8 blocs. Les quais, pages et annonces utilisent le protocole PIDS existant.
- La taille physique, la densité raster et le zoom sont indépendants. La résolution
  représente une enveloppe maximale : le ratio physique est conservé, sans étirer
  le contenu. Le cache limite aussi les atlas à environ 4 mégapixels / 4096 pixels
  par axe ; les grandes listes d’annonces peuvent donc réduire la densité effective.
- Adaptation inspirée des seuils de `arnoclr/panam/src/components/Screen.vue` :
  deux colonnes sous un ratio hauteur/largeur de 0,6, sinon départs au-dessus des
  annonces ; bandeau masqué sous 0,4. Les chiffres gardent leurs proportions,
  les annonces reviennent à la ligne. Sur une surface très étroite/basse, priorité
  au premier départ. Un message est limité à 12 lignes ; les annonces tournent
  avec les mêmes priorités et durées que le PANAM suspendu.
- Le zoom réduit le viewport logique comme dans un navigateur : le texte grossit,
  les espaces et retours à la ligne s’adaptent, sans agrandir le rectangle dans le monde.

## Boîtier photographié

Les deux demi-boîtiers restent ouverts à leur jonction : les joues métalliques
sont aux extrémités extérieures, plus au milieu du titre. Les dos des LCD sont
reculés derrière les plans inclinés à 2,6° vers le bas (haut plus écarté que bas).
Les deux cartouches dépassent en haut et en bas du corps central graphite.
Les joues ont des parties supérieures ivoire, une zone centrale noire et des
retours inférieurs à la couleur de la ligne. La lèvre basse suit cette même couleur,
avec mise à jour des deux demi-blocs uniquement lorsque la couleur change.
Le canal inférieur est en retrait, avec deux tiges de suspension et petites platines.
Le générateur `tools/build_panam_model.py` ne produit que le JSON de ce boîtier.

Vérification de cette révision : ressources JSON, géométrie, contrats de réglages
et syntaxe Java inspectés sans compilation du mod ni lancement du jeu.
Le rendu en jeu et la compatibilité finale avec d’autres mods restent à vérifier
lors d’une future session de test autorisée.
