"""Generate a seamless half-casing matching the PIXEL world coordinate contract."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
dest=ROOT/'fabric/src/main/resources/assets/mtr/models/block/panam_pids.json'
faces=('north','south','east','west','up','down')
def box(lo,hi,material='#metal',omit=()):
    return {'from':lo,'to':hi,'faces':{side:{'texture':material} for side in faces if side not in omit}}
model={'ambientocclusion':False,'textures':{'particle':'mtr:block/metal','metal':'mtr:block/metal','black':'mtr:block/black'},'elements':[
    box([6.1,.75,0],[9.9,15.75,16], '#black', ('north','south','up','down')),
    box([5.9,.75,0],[10.1,1.15,16],omit=('north',)),
    box([5.9,15.35,0],[10.1,15.75,16],omit=('north',)),
    box([5.9,1.15,15.6],[10.1,15.35,16],omit=('up','down')),
    box([7.4,15.75,8],[8.6,16,9]),
]}
dest.write_text(json.dumps(model,indent=2)+'\n')
# FACING is the direction towards the other half, inherited from horizontal MTR PIDS.
# Rotating identical open-ended halves by opposite facings joins their open z=0 edges.
states={'variants':{f'facing={face}':{'model':'mtr:block/panam_pids',**({'y':rot} if rot else {})} for face,rot in [('north',0),('east',90),('south',180),('west',270)]}}
(dest.parents[2]/'blockstates/panam_pids.json').write_text(json.dumps(states,indent=2)+'\n')
