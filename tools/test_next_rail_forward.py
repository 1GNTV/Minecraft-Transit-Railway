#!/usr/bin/env python3
"""Camera-basis regression for GPU rail culling. Math/source test, NOT a GL test."""
import math
from pathlib import Path

root=Path(__file__).resolve().parents[1]
source=(root/"fabric/src/main/java/org/mtr/mod/render/GpuDrivenRailCommands.java").read_text(encoding="utf-8")
required=(
    "float forwardX=(float)(-Math.sin(yaw)*cosPitch);",
    "float forwardY=(float)(-Math.sin(pitch));",
    "float forwardZ=(float)(Math.cos(yaw)*cosPitch);",
    "GL43.glUniform3f(forwardLoc,forwardX,forwardY,forwardZ);",
    "bool outside=d2>1024.0 && horizontalForward>0.35",
    "dot(delta.xz,mtr_Forward.xz)<-radius*horizontalForward",
    "float radius=length(halfExtent)+16.0;",
)
for token in required:
    assert token in source, f"Camera basis/culling contract changed: {token}"

def forward(yaw, pitch):
    y,p=map(math.radians,(yaw,pitch))
    return (-math.sin(y)*math.cos(p),-math.sin(p),math.cos(y)*math.cos(p))

def near(a,b):
    return all(abs(x-y)<1e-6 for x,y in zip(a,b))

assert near(forward(0,0),(0,0,1)), "yaw 0 must face south"
assert near(forward(90,0),(-1,0,0)), "yaw 90 must face west"
assert near(forward(180,0),(0,0,-1)), "yaw 180 must face north"
assert near(forward(-90,0),(1,0,0)), "yaw -90 must face east"
assert near(forward(0,90),(0,-1,0)), "positive pitch looks down"
assert near(forward(0,-90),(0,1,0)), "negative pitch looks up"
for yaw in range(-180,181,15):
    for pitch in (-89,-60,-30,0,30,60,89):
        v=forward(yaw,pitch)
        assert abs(sum(x*x for x in v)-1)<1e-6

# Mirror the GPU's conservative horizontal rear-hemisphere test.
# MTR rail distance is selected by the CPU; the compute shader MUST NOT
# impose vanilla chunk distance (which hid far-away but visible rail).
def culled(center, half, eye, view):
    radius=math.sqrt(sum(x*x for x in half))+16.0
    delta=tuple(center[i]-eye[i] for i in range(3))
    d2=delta[0]**2+delta[2]**2
    horizontal=math.hypot(view[0],view[2])
    return d2>1024.0 and horizontal>0.35 and (delta[0]*view[0]+delta[2]*view[2])<-radius*horizontal

eye=(0,64,0)
assert not culled((0,64,20),(2,2,2),eye,forward(0,0))
assert not culled((0,64,-20),(2,2,2),eye,forward(0,0)), "32-block near bubble must remain visible"
assert culled((0,64,-80),(2,2,2),eye,forward(0,0)), "distant rear rail can be culled"
assert not culled((0,64,80),(2,2,2),eye,forward(0,0))
assert not culled((0,64,-80),(200,2,200),eye,forward(0,0)), "large geometry must fail open"
assert not culled((0,64,1000),(2,2,2),eye,forward(0,0)), "MTR long-distance rails must remain visible"
assert not culled((0,64,-80),(2,2,2),eye,forward(0,89)), "near-vertical view must fail open"
assert not culled((0,64,-80),(2,2,2),eye,forward(0,-89)), "near-vertical view must fail open"
# Camera subtraction must occur in world-coordinate double precision
# before the GPU receives sector-local floats. Avoid world-border holes.
assert 'uniform vec3 mtr_EyeLocal;' in source
assert 'vec3 delta=center-mtr_EyeLocal;' in source
assert 'eye.getXMapped()-originX' in source
assert 'eye.getYMapped()-originY' in source
assert 'eye.getZMapped()-originZ' in source
regions=(root/"fabric/src/main/java/org/mtr/mod/render/RegionVertexRanges.java").read_text()
static=(root/"fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java").read_text()
assert "double originX,originY,originZ;" in regions
assert "worldOrigin(VertexArray array,double x,double y,double z)" in regions
assert "sector.x()*16D,sector.y()*16D,sector.z()*16D" in static
for pos in (-29999999.875,-17000000.5,-1.125,0,17500000.75,29999999.125):
    origin=math.floor(pos/16)*16
    local=pos-origin
    assert abs(local)<=16 and abs((origin+local)-pos)<1e-9
print("PASS: Minecraft camera yaw/pitch, conservative rail culling, precise world-border sector coordinates")
