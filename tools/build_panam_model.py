"""Generate PANAM casing resources only; this does not compile the mod.

NORTH/SOUTH half origins join at z=0. Only z=16 is an outer end.
The renderer owns smooth LCD planes tilted by 2.6 degrees. Vanilla block
rotations cannot express 2.6 degrees, so hidden backs use recessed slices.
"""
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json'
FACES=('north','south','east','west','up','down')
elements=[]
def box(name,lo,hi,material='black',omit=()):
    elements.append({'comment':name,'from':[round(v,5) for v in lo],
        'to':[round(v,5) for v in hi],
        'faces':{f:{'texture':'#'+material} for f in FACES if f not in omit}})
a=math.radians(2.6)
top=12.75+.164*16*math.sin(a)
bottom=top-13.15*math.cos(a)
def glass_x(y):
    return 8+.164*16*math.cos(a)+(top-y)*math.tan(a)
box('Recessed central spine',[6.65,-.12,0],[9.35,13.15,16],omit=('north',))
box('Underside cable channel',[6.9,-.24,0],[9.1,.04,16],omit=('north','up'))
for i in range(32):
    y0=bottom+(top-bottom)*i/32
    y1=bottom+(top-bottom)*(i+1)/32
    outer=glass_x(y1)-.055
    omit=('north','up','down')
    box('Recessed LCD back right %02d'%i,[9.35,y0,0],[outer,y1,15.72],omit=omit)
    box('Recessed LCD back left %02d'%i,[16-outer,y0,0],[6.65,y1,15.72],omit=omit)
    box('Outer dark cheek right %02d'%i,[9.35,y0,15.72],[glass_x(y1)+.025,y1,15.87],omit=('up','down'))
    box('Outer dark cheek left %02d'%i,[16-glass_x(y1)-.025,y0,15.72],[6.65,y1,15.87],omit=('up','down'))
    box('Thin pale end trim right %02d'%i,[9.35,y0,15.87],[glass_x(y1)+.03,y1,16],material='metal',omit=('up','down'))
    box('Thin pale end trim left %02d'%i,[16-glass_x(y1)-.03,y0,15.87],[6.65,y1,16],material='metal',omit=('up','down'))
box('Outer recessed end closure',[6.65,bottom,15.87],[9.35,13.15,16],material='metal')
box('Slim upper rail right',[9.35,top+.025,0],[10.66,13.16,16],material='metal',omit=('north',))
box('Slim upper rail left',[5.34,top+.025,0],[6.65,13.16,16],material='metal',omit=('north',))
box('Green projecting lip carrier right',[9.35,-.68,0],[11.45,bottom-.025,16],material='green',omit=('north',))
box('Green projecting lip carrier left',[4.55,-.68,0],[6.65,bottom-.025,16],material='green',omit=('north',))
box('Thin lower metal return right',[9.35,-.74,0],[11.27,-.68,16],material='metal',omit=('north','up'))
box('Thin lower metal return left',[4.73,-.74,0],[6.65,-.68,16],material='metal',omit=('north','up'))
box('Ceiling suspension rod',[7.78,13.15,7.78],[8.22,15.72,8.22],material='metal')
box('Ceiling anchor plate',[7.30,15.70,7.30],[8.70,16,8.70],material='metal')
box('Underside screw right',[9.95,-.755,12],[10.14,-.74,12.19],material='black',omit=('up',))
box('Underside screw left',[5.86,-.755,12],[6.05,-.74,12.19],material='black',omit=('up',))
model={'ambientocclusion':False,'textures':{'particle':'mtr:block/metal',
    'metal':'mtr:block/metal','black':'mtr:block/black','green':'mtr:block/panam_green'},'elements':elements}
DEST.write_text(json.dumps(model,indent=2)+'\n')
