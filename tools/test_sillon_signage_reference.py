#!/usr/bin/env python3
"""Source-level guardrails; Forge Java compiler and visual review are separate gates."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
canvas=(root/"fabric/src/main/java/org/mtr/mod/client/SillonSignageCanvas.java").read_text()
layout=(root/"fabric/src/main/java/org/mtr/mod/client/SillonRATPSignageLayouts.java").read_text()
spec=(root/"fabric/src/main/java/org/mtr/mod/data/SillonSignageSpec.java").read_text()
assert "if(SillonRATPSignageLayouts.draw(g,s,w,h))return;" in canvas
assert '"metro","metro_subtitle","rer","direction","transfer","interchange","exit","sector"' in spec
assert "Never counterfeit official line pictograms" in canvas
assert 'center(g,"?"' in canvas
assert 'case "direction","transfer"' in layout
assert 'case "interchange"' in layout
assert 'servicesColor=0x009C94' in layout
assert 'case "exit"' in layout
assert 'case "sector"' in layout
assert 'case "services"' in layout
assert 'g.fillRect(0,0,w,stripe)' in layout
assert '"Sortie"' in layout
assert '"Sorties à renseigner"' in layout
assert '"Plan non renseigné"' in layout
assert '"Station","Correspondance","Terminus"' not in layout
assert '"Entrée","Billetterie","Quais","Sortie"' not in layout
assert 'static final int NAVY=0x101B54' in layout
assert 'BLUE=0x101E5E' in layout
assert 'GREY=0x666971' in layout
assert 'OCHRE=0x836130' in layout
assert 'SillonSignageCanvas.font(' in layout
assert 'SillonSignageCanvas.drawIcons(' in layout
assert 'SillonSignageCanvas.drawArrow(' in layout
assert 'Parisine-Regular.otf' in canvas and 'Parisine-Bold.otf' in canvas
print("PASS: real family-specific RATP layouts, palette semantics, absent-resource honesty")
