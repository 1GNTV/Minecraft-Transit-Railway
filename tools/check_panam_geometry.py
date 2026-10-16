"""Geometry/color regression checks against signed production transforms; no build/game."""
from pathlib import Path
import json,math,re,runpy
ROOT=Path(__file__).resolve().parents[1]
model_path=ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json'
model=json.loads(model_path.read_text())
surface=(ROOT/'fabric/src/main/java/org/mtr/mod/render/pixel/PanamSurface.java').read_text()
renderer=(ROOT/'fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java').read_text()
width=float(re.search(r'WORLD_WIDTH = ([\d.]+)f',surface)[1])*16
height=float(re.search(r'WORLD_HEIGHT = ([\d.]+)f / 16',surface)[1])
origin=float(re.search(r'TOP = ([\d.]+)f / 16',surface)[1])
depth=float(re.search(r'g.translate\(-PanamSurface.WORLD_WIDTH/2,0,-([\d.]+)f',renderer)[1])*16
angle=float(re.search(r'g.rotateXDegrees\((-?[\d.]+)f',renderer)[1])*math.pi/180
hi=origin-depth*math.sin(angle);lo=hi-height*math.cos(angle)
def front(y):return 8+depth*math.cos(angle)-(hi-y)*math.tan(angle)
# Apply Rx(pitch), Rz(180), then either horizontal facing rotation.
# The outward normal of BOTH actual display planes must have negative world Y.
normal_y=-math.sin(angle)
assert normal_y<0,'LCD points up: local pitch sign is inverted'
assert front(hi)>front(lo),'Top edges must be farther apart than bottom edges'
checked=0
for e in model['elements']:
 x0,y0,z0=e['from'];x1,y1,z1=e['to']
 assert 0<=x0<x1<=16 and -1<=y0<y1<=16 and 0<=z0<z1<=16,e['comment']
 if max(y0,lo)>=min(y1,hi) or z0>=width/2:continue
 # Both ends of the signed linear plane bound the complete intersection interval.
 limit=min(front(max(y0,lo)),front(min(y1,hi)))
 assert x1<limit-.001,(e['comment'],'front glass collision')
 assert x0>16-limit+.001,(e['comment'],'rear glass collision')
 checked+=1
body=next(e for e in model['elements'] if e['comment']=='Recessed graphite central body')
assert body['from'][1]>lo+.5 and body['to'][1]<hi-.5,'Both LCDs must protrude beyond the central body vertically'
for e in model['elements']:
 if 'end trim' in e['comment'].lower():assert e['from'][2]>width/2
lips=[e for e in model['elements'] if 'lip carrier' in e['comment'].lower()]
assert len(lips)==2
for e in lips:
 assert all(f['texture']=='#white' and f.get('tintindex')==0 for f in e['faces'].values()),'Lip must use the route color, never a fixed green texture'
cheeks=[e for e in model['elements'] if 'cartridge cheek' in e['comment'].lower()]
assert any(next(iter(e['faces'].values()))['texture']=='#ivory' for e in cheeks)
assert any(next(iter(e['faces'].values()))['texture']=='#black' for e in cheeks)
assert any(next(iter(e['faces'].values())).get('tintindex')==0 for e in cheeks)
rods=[e for e in model['elements'] if 'suspension rod' in e['comment'].lower()]
assert len(rods)==1 and 7<rods[0]['from'][2]<9
before=model_path.read_bytes();runpy.run_path(str(ROOT/'tools/build_panam_model.py'));assert model_path.read_bytes()==before
print(f'PASS: downward normals, top wider than bottom, {checked} non-intersecting boxes, protruding LCDs, route-tinted lips and three-tone cheeks')
