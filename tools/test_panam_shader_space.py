"""No-build regression: native BER matrices must preserve all world-space panel positions."""
from pathlib import Path
import math,re
ROOT=Path(__file__).resolve().parents[1]
src=(ROOT/'fabric/src/main/java/org/mtr/mod/render/RenderPANAMPIDS.java').read_text()

def translation(method):
    body=src.split('private static void '+method+'(',1)[1]
    return body.split('g.translate(',1)[1].split(';',1)[0].rsplit(')',1)[0]

def xyz(expression,pos,nxt,settings):
    for name,v in [('pos',pos),('next',nxt)]:
        for axis,value in zip('XYZ',v):expression=expression.replace(f'{name}.get{axis}()',repr(value))
    for axis,value in zip('xyz',settings):expression=expression.replace('s.'+axis,repr(value))
    return eval('('+expression+')',{'__builtins__':{}},{})

def plus(a,b):return tuple(x+y for x,y in zip(a,b))
def minus(a,b):return tuple(x-y for x,y in zip(a,b))
def close(a,b):return all(math.isclose(x,y,rel_tol=0,abs_tol=1e-8) for x,y in zip(a,b))

# The old deferred origin was the camera; the new native BER origin is the block.
# Compare in world space, including negative coordinates and the world border.
checks=0
for pos in [(0,0,0),(-17,64,-33),(19,-20,15),(29999900,255,-29999900)]:
 for camera in [(3.125,5.5,-9.75),(-100.75,12.25,85.5)]:
  for direction in [(0,0,-1),(1,0,0),(0,0,1),(-1,0,0)]:
   nxt=plus(pos,direction)
   local=xyz(translation('transform'),pos,nxt,(0,0,0))
   expected=((pos[0]+nxt[0])/2+.5-camera[0],pos[1]-camera[1],(pos[2]+nxt[2])/2+.5-camera[2])
   assert close(plus(minus(pos,camera),local),expected),'hanging display moved with native BER origin'
   checks+=1
  for settings in [(0,0,0),(-16,16,7.25),(12.5,-3.125,-16)]:
   local=xyz(translation('transformScreen'),pos,pos,settings)
   expected=tuple(pos[i]+.5+settings[i]-camera[i] for i in range(3))
   assert close(plus(minus(pos,camera),local),expected),'screen-only offsets applied twice'
   checks+=1

# Shadow invocations must not mutate timelines, request atlases or rebuild world geometry.
render=src.split('@Override public void render(',1)[1].split('private static void transformScreen(',1)[0]
code=re.sub(r'//[^\n]*','',render)
assert code.index('if(OptimizedRenderer.renderingShadows())return;')<code.index('PANELS.computeIfAbsent')
assert 'MainRenderer.scheduleRender(' not in code
assert 'g.createVertexConsumer(MoreRenderLayers.getLight2(entry.texture))' in code
assert 'finally' in code and 'g.pop()' in code
print(f'PASS: {checks} native BER/camera position comparisons; direct shader context and early shadow guard')
