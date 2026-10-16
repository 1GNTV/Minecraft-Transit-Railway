"""Check casing/glass intersection using source transforms; no Java/build/game required."""
from pathlib import Path
import json,math,re
ROOT=Path(__file__).resolve().parents[1]
model=json.loads((ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json').read_text())
surface=(ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel/PanamSurface.java').read_text()
renderer=(ROOT/'fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java').read_text()
width=float(re.search(r'WORLD_WIDTH = ([\d.]+)f',surface)[1])*16
height=float(re.search(r'WORLD_HEIGHT = ([\d.]+)f / 16',surface)[1])
top=float(re.search(r'TOP = ([\d.]+)f / 16',surface)[1])
depth=float(re.search(r'g.translate\(-PanamSurface.WORLD_WIDTH/2,0,-([\d.]+)f',renderer)[1])*16
angle=abs(float(re.search(r'g.rotateXDegrees\((-?[\d.]+)f',renderer)[1]))*math.pi/180
hi=top+depth*math.sin(angle);lo=hi-height*math.cos(angle)
def front(y):return 8+depth*math.cos(angle)+(hi-y)*math.tan(angle)
checked=0
for e in model['elements']:
 x0,y0,z0=e['from'];x1,y1,z1=e['to']
 assert 0<=x0<x1<=16 and -1<=y0<y1<=16 and 0<=z0<z1<=16,e.get('comment')
 if max(y0,lo)>=min(y1,hi) or z0>=width/2:continue
 # Along the active UI span, every casing box must be wholly behind BOTH
 # tilted glass planes. The plane is linear, so its highest y is limiting.
 y=min(y1,hi)
 assert x1<front(y)-.001,(e['comment'],'front glass collision')
 assert x0>16-front(y)+.001,(e['comment'],'rear glass collision')
 checked+=1
for e in model['elements']:
 if 'end trim' in e.get('comment','').lower():assert e['from'][2]>width/2
rods=[e for e in model['elements'] if 'suspension rod' in e.get('comment','').lower()]
assert len(rods)==1 and 7<rods[0]['from'][2]<9 # one per half -> two across full display
# The resource generator must be deterministic and preserve the reviewed geometry.
import runpy
before=(ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json').read_bytes()
runpy.run_path(str(ROOT/'tools/build_panam_model.py'))
assert (ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json').read_bytes()==before
print(f'PASS: {checked} inner casing boxes behind both LCD planes; outer end trims, rods, bounds and deterministic JSON')
