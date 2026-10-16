"""Generate PANAM casing JSON, without compiling the mod.

Open z=0 halves join at the centre; only z=16 receives side cheeks.
Top edges sit farther apart than bottom edges: both LCD normals point down.
The paired cartridges extend above/below the recessed graphite centre body.
"""
import json,math,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json'
FACES=('north','south','east','west','up','down')
elements=[]
def box(name,lo,hi,material='black',omit=(),tint=None):
    faces={f:{'texture':'#'+material,**({'tintindex':tint} if tint is not None else {})} for f in FACES if f not in omit}
    elements.append({'comment':name,'from':[round(v,5) for v in lo],'to':[round(v,5) for v in hi],'faces':faces})
# Derive the glass envelope from the production transform (signed pitch matters).
surface=(ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel/PanamSurface.java').read_text()
renderer=(ROOT/'fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java').read_text()
a=math.radians(float(re.search(r'g.rotateXDegrees\((-?[\d.]+)f',renderer)[1]))
d=float(re.search(r'g.translate\(-PanamSurface.WORLD_WIDTH/2,0,-([\d.]+)f',renderer)[1])*16
origin=float(re.search(r'TOP = ([\d.]+)f / 16',surface)[1])
height=float(re.search(r'WORLD_HEIGHT = ([\d.]+)f / 16',surface)[1])
top=origin-d*math.sin(a)
bottom=top-height*math.cos(a)
def glass_x(y):return 8+d*math.cos(a)-(top-y)*math.tan(a)
box('Recessed graphite central body',[6.65,.8,0],[9.35,12.0,16],material='graphite',omit=('north',))
box('Underside cable channel',[6.9,.46,0],[9.1,.8,16],material='graphite',omit=('north','up'))
for i in range(32):
    y0=bottom+(top-bottom)*i/32;y1=bottom+(top-bottom)*(i+1)/32
    front=min(glass_x(y0),glass_x(y1))
    # Black cartridge backs remain behind the continuous renderer-owned LCD.
    box('Recessed LCD back right %02d'%i,[9.35,y0,0],[front-.055,y1,15.68],omit=('north','up','down'))
    box('Recessed LCD back left %02d'%i,[16-front+.055,y0,0],[6.65,y1,15.68],omit=('north','up','down'))
    # Pale upper caps, dark middle cheeks, route-colored lower returns.
    material='ivory' if y0>=top-3.1 else 'white' if y1<=bottom+2.05 else 'black'
    tint=0 if material=='white' else None
    box('Outer cartridge cheek right %02d'%i,[9.35,y0,15.68],[front+.025,y1,15.9],material,omit=('up','down'),tint=tint)
    box('Outer cartridge cheek left %02d'%i,[16-front-.025,y0,15.68],[6.65,y1,15.9],material,omit=('up','down'),tint=tint)
    edge='white' if material=='white' else 'ivory'
    edge_tint=0 if edge=='white' else None
    # Only a fine pale edge, rather than painting the entire side silver.
    box('Thin outer end trim right %02d'%i,[front-.055,y0,15.9],[front+.04,y1,16],edge,omit=('up','down'),tint=edge_tint)
    box('Thin outer end trim left %02d'%i,[16-front-.04,y0,15.9],[16-front+.055,y1,16],edge,omit=('up','down'),tint=edge_tint)
box('Outer graphite centre end',[6.65,.8,15.9],[9.35,12.0,16],material='graphite')
far=glass_x(top)
box('Pale upper cap right',[9.35,top+.025,0],[far+.04,13.04,16],material='ivory',omit=('north',))
box('Pale upper cap left',[16-far-.04,top+.025,0],[6.65,13.04,16],material='ivory',omit=('north',))
far=glass_x(bottom)
box('Route-colored lip carrier right',[9.35,bottom-.38,0],[far+.16,bottom-.025,16],material='white',tint=0,omit=('north',))
box('Route-colored lip carrier left',[16-far-.16,bottom-.38,0],[6.65,bottom-.025,16],material='white',tint=0,omit=('north',))
box('Dark lower return right',[9.35,bottom-.43,0],[far+.10,bottom-.38,16],material='graphite',omit=('north','up'))
box('Dark lower return left',[16-far-.10,bottom-.43,0],[6.65,bottom-.38,16],material='graphite',omit=('north','up'))
box('Ceiling suspension rod',[7.78,12.0,7.78],[8.22,15.72,8.22],material='metal')
box('Ceiling anchor plate',[7.30,15.70,7.30],[8.70,16,8.70],material='metal')
model={'ambientocclusion':False,'textures':{'particle':'mtr:block/panam_graphite','metal':'mtr:block/metal',
    'black':'mtr:block/black','white':'mtr:block/white','ivory':'mtr:block/panam_ivory','graphite':'mtr:block/panam_graphite'},'elements':elements}
DEST.write_text(json.dumps(model,indent=2)+'\n')
