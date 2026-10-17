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
    "if(!outside && d2>1024.0)",
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

# Mirror the GPU conservative distance/rear-hemisphere test.
def culled(center, half, eye, view, render_distance):
    radius=math.sqrt(sum(x*x for x in half))+16.0
    delta=tuple(center[i]-eye[i] for i in range(3))
    d2=delta[0]**2+delta[2]**2
    outside=d2>(render_distance+radius)**2
    if not outside and d2>1024.0:
        outside=sum(delta[i]*view[i] for i in range(3))<-radius
    return outside

eye=(0,64,0)
assert not culled((0,64,20),(2,2,2),eye,forward(0,0),128)
assert not culled((0,64,-20),(2,2,2),eye,forward(0,0),128), "32-block near bubble must remain visible"
assert culled((0,64,-80),(2,2,2),eye,forward(0,0),128), "distant rear rail can be culled"
assert not culled((0,64,80),(2,2,2),eye,forward(0,0),128)
assert not culled((0,64,-80),(200,2,200),eye,forward(0,0),128), "large geometry must fail open"
print("PASS: Minecraft camera yaw/pitch, normalized forward vector, conservative rail culling")
