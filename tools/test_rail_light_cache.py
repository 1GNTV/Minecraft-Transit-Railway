"""Compile and verify per-frame rail light cache + packed position uniqueness."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "fabric/src/main/java/org/mtr/mod/render/RailLightFrameCache.java"
STUBS = {
"org/mtr/mapping/holder/BlockPos.java": """package org.mtr.mapping.holder;
public final class BlockPos{
private final int x,y,z;
public BlockPos(int a,int b,int c){x=a;y=b;z=c;}
public int getX(){return x;} public int getY(){return y;}public int getZ(){return z;}
}
""",
"org/mtr/mapping/holder/ClientWorld.java": """package org.mtr.mapping.holder;
import org.mtr.mapping.mapper.LightType;
public final class ClientWorld{
public int calls,light=3,sky=15;
public int getLightLevel(LightType t,BlockPos p){calls++;return t.block?light:sky;}
}
""",
"org/mtr/mapping/mapper/LightType.java": """package org.mtr.mapping.mapper;
public final class LightType{
public final boolean block;
private LightType(boolean b){block=b;}
public static LightType getBlockMapped(){return new LightType(true);}
public static LightType getSkyMapped(){return new LightType(false);}
}
""",
"org/mtr/mapping/mapper/LightmapTextureManager.java": """package org.mtr.mapping.mapper;
public final class LightmapTextureManager{
public static int pack(int a,int b){return a*256+b;}
}
""",
"org/mtr/libraries/it/unimi/dsi/fastutil/longs/Long2IntOpenHashMap.java": """package org.mtr.libraries.it.unimi.dsi.fastutil.longs;
import java.util.*;
public final class Long2IntOpenHashMap{
private final Map<Long,Integer> data=new HashMap<>();private int missing;
public Long2IntOpenHashMap(int capacity){}
public void defaultReturnValue(int v){missing=v;}
public int get(long key){return data.getOrDefault(key,missing);}
public void put(long key,int value){data.put(key,value);}
public int size(){return data.size();}public void clear(){data.clear();}
}
""",
"Verify.java": """import org.mtr.mod.render.RailLightFrameCache;
import org.mtr.mapping.holder.*;
public final class Verify{
static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
public static void main(String[] args){
ClientWorld world=new ClientWorld();
BlockPos a=new BlockPos(-19,-64,22),b=new BlockPos(22,-64,-19);
RailLightFrameCache.beginFrame();
int original=RailLightFrameCache.get(world,a);
check(RailLightFrameCache.get(world,a)==original && world.calls==2,"stable lookup");
int other=RailLightFrameCache.get(world,b);
check(other==original && world.calls==4,"distinct coordinates do not collide");
world.light=7;
check(RailLightFrameCache.get(world,a)==original,"same frame uses light snapshot");
RailLightFrameCache.beginFrame();
check(RailLightFrameCache.get(world,a)!=original && world.calls==6,"next frame re-samples dynamic light");
System.out.println("PASS: same-frame reuse, unique packed coordinates, tick invalidation");
}
}
"""
}
with TemporaryDirectory() as dirname:
    root = Path(dirname)
    for name, body in STUBS.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    subprocess.run(["javac", "-d", str(root), str(SOURCE)] +
                   [str(root / path) for path in STUBS], check=True)
    subprocess.run(["java", "-cp", str(root), "Verify"], check=True)
