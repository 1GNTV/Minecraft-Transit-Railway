# Sillon Signage — reference evidence and fidelity gate

Updated 2026-10-18. This document defines the historical and operational sources
for the Forge 1.20.1 signage rebuild. It does not claim operator certification
or measured 1:1 manufacturer dimensions.

## Official references
- RATP "Fiches équipements", section **Signalétique**: white directional
  grounds guide passengers toward transport and standard amenities, navy blue
  guides toward exits, and grey identifies special services. These are *corridor
  wayfinding principles*, not rules for heritage station name plaques.
  https://www.ratp.fr/sites/default/files/inline-files/fiches_equipements_1.pdf
- RATP "La typographie du métro": pre-1996 historical families vary; Parisine
  was adopted in 1996. Do not apply it indiscriminately to CMP/Nord-Sud eras.
  https://www.ratp.fr/decouvrir/patrimoine/la-typographie-du-metro
- IDFM PRIM signalétique: references for permitted icons, line signs,
  wayfinding, typefaces Grand Paris and IDF Voyageur and RGB color charts.
  https://prim.iledefrance-mobilites.fr/fr/chartes-et-prescriptions/prescriptions-signaletiques
- IDFM ILICO: official generation endpoint for line identification SVGs.
  https://prim.iledefrance-mobilites.fr/fr/jeux-de-donnees/idfm-signaletique
- RATP plans: authoritative line stops and interchange relationships:
  https://www.ratp.fr/plans
- RATP Renouveau frises / Châtelet sectors Seine (blue), Rivoli (gold),
  Canopée (silver):
  https://www.ratp.fr/decouvrir/patrimoine/les-frises-du-metro
- MTR France: optional at-runtime original line/sign pictograms:
  https://github.com/MTR-France-Team/MTR-France-Addon/tree/master/src/main/resources/assets/mtr/mtr_france_sign

## Reference photos supplied by the player
- Saint-Sulpice, Monceau, Pigalle: enamel-style navy station plaques, big white name.
- Saint-Denis Porte de Paris, Rambuteau: navy station names with ochre subtitles.
- Châtelet–Les Halles: RER station identity and sector differentiation.
- Châtelet Rivoli: grey sector band, transport icons on white strip,
  navy exit field with numbered street destinations.
- Oberkampf / Strasbourg–Saint-Denis: different wayfinding/transfer panel
  families, multiple icons, dedicated direction arrow.
- Vertical arrows: white background with colored line badge and dark arrow.
- Station strips and projected display screens are not ordinary route plaques.

## Renderer contract
SillonRATPSignageLayouts uses separate layouts per real reference family,
not one recolored rectangle:
- metro / metro_subtitle: dark navy plate; optional ochre subtitle band;
  centered Parisine with one or two lines.
- direction / transfer: white base, navy copy, native MTR France icons.
- interchange: RATP official stacked white transport + teal services + navy exit, with separate zones and no fictitious destinations.
- exit: white transport icon strip + blue Sortie heading, numbered exit rows.
- sector: grey location header, white mode strip, blue exit field.
- services: grey ground rather than fake universal navy.
- vertical: white pillar panel, large line icon above and arrow below.
- platform / route: only real MTR map data or explicitly entered stops.
- map: no invented spatial graph; clearly state unconfigured plans.
- missing optional icons: visible neutral question-mark placeholders rather
  than unofficial colored emulations of operator logos.
- custom / digital remain separately supported but are NOT automatically
  asserted to be photographic reproductions.
- RATP Parisine fonts already loaded from the Sillon bundled font resources.
  IDFM modern fonts are separate and require their own distribution rights.

## Fidelity/qualification rules
Proportions used by the templates are measured estimates from the examples,
not manufacturer drawings. Color swatches are image-derived starting points,
not the exact published IDFM RGB/CMYK values. Do NOT label them 1:1 exact.

The source CI regression validates family selection, text/font wiring, palette
semantics and removal of fictitious diagrams. Compilation is verified by Forge
CI, but neither is a visual approval. The release gate requires photographed
side-by-side comparisons, shader testing, original icon rendering and world
save/multiplayer checks. No release may claim every panel is fully accurate
without these tests.

## Photo-calibrated editable presets (not licensed official assets)
Eleven editable scene presets are offered in the in-game brush editor. Based
on the player's photographed real metro examples, not fictional station names:
Monceau; Pigalle; Saint-Sulpice; Rambuteau/Centre Georges Pompidou;
Saint-Denis/Porte de Paris/Stade de France; Châtelet–Les Halles/Forum;
Châtelet/Rivoli numbered exits; Strasbourg–Saint-Denis; official RATP-style
multi-band wayfinding; and an information totem.

Approximate sampled sRGB values from the supplied compressed photographs:
- Rambuteau navy #21275B, lower band #51330C;
- Saint-Denis Porte de Paris navy #2A4078, lower band #875E29.

Photographic white balance and illumination vary. These are just starting
settings and must not be represented as official RATP material specifications.
The presets preserve those background / accent choices and store the title as
a *fixed* value (stationAuto=false), unless the player enables MTR auto naming.

The in-game renderer preserves two-line explicit titles (pipe character)
and the per-preset background/accent to reproduce photographed differences.
