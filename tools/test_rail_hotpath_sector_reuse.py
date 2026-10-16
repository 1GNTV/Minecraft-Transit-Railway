#!/usr/bin/env python3
"""Compile actual Sillon spatial selection against lightweight JVM fixtures.

Checks cache reuse during camera motion, candidate completeness and sync expiry.
This is not a synthetic FPS benchmark.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"fabric/src/main/java"
ENGINE=SRC/"org/mtr/mod/render/strata/StrataRailEngine.java"
SPATIAL=SRC/"org/mtr/mod/client/RailSpatialIndex.java"
SNAP=SRC/"org/mtr/mod/render/RailGeometrySnapshots.java"
assert 'return candidates;' in ENGINE.read_text()
assert 'new ArrayList<' not in ENGINE.read_text()
assert 'SNAPSHOTS.values().stream()' not in SNAP.read_text()
assert 'retainedEntries=SNAPSHOTS.size()' in SNAP.read_text()

STUBS={
'org/mtr/libraries/it/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap.java': 'package org.mtr.libraries.it.unimi.dsi.fastutil.longs;public class Long2ObjectOpenHashMap<T> extends java.util.HashMap<Long,T>{}',
'org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList.java': 'package org.mtr.libraries.it.unimi.dsi.fastutil.objects;public class ObjectArrayList<T> extends java.util.ArrayList<T>{public ObjectArrayList(){}public ObjectArrayList(int n){super(n);}public ObjectArrayList(java.util.Collection<? extends T> c){super(c);}}',
'org/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectOpenHashSet.java': 'package org.mtr.libraries.it.unimi.dsi.fastutil.objects;public class ObjectOpenHashSet<T> extends java.util.HashSet<T>{public ObjectOpenHashSet(java.util.Collection<? extends T> c){super(c);}}',
'org/mtr/core/data/RailMath.java': 'package org.mtr.core.data;public class RailMath{public double minX,minY,minZ,maxX,maxY,maxZ;public RailMath(double x,double z){minX=x-2;maxX=x+2;minZ=z-2;maxZ=z+2;}}',
'org/mtr/core/data/Rail.java': 'package org.mtr.core.data;public class Rail{public final RailMath railMath;public Rail(double x,double z){railMath=new RailMath(x,z);}}',
'org/mtr/mapping/holder/Vector3d.java': 'package org.mtr.mapping.holder;public class Vector3d{final double x,z;public Vector3d(double x,double z){this.x=x;this.z=z;}public double getXMapped(){return x;}public double getZMapped(){return z;}}',
'org/mtr/mapping/mapper/MinecraftClientHelper.java': 'package org.mtr.mapping.mapper;public class MinecraftClientHelper{public static int getRenderDistance(){return 4;}}',
'org/mtr/mod/Init.java': 'package org.mtr.mod;public class Init{public static final Log LOGGER=new Log();public static class Log{public void info(String fmt,Object... args){}}}',
'org/mtr/mod/render/TrainVisibility.java': 'package org.mtr.mod.render;public class TrainVisibility{public static boolean railShadowVisible(double a,double b,double c,double d,double e,double f){return true;}}',
'org/mtr/mod/client/MinecraftClientData.java': '''
package org.mtr.mod.client;
import org.mtr.core.data.Rail;
public class MinecraftClientData {
 private static final MinecraftClientData INSTANCE=new MinecraftClientData();
 public static MinecraftClientData getInstance(){return INSTANCE;}
 public final RailSpatialIndex railSpatialIndex=new RailSpatialIndex();
 public final java.util.Map<String,RailWrapper> railWrapperList=new java.util.LinkedHashMap<>();
 public long networkRevision=1;
 public static class RailWrapper {
  public boolean shouldRender=true;
  public final Vec startVector,endVector;
  private final Rail rail;
  public RailWrapper(Rail r){rail=r;startVector=new Vec(r.railMath.minX,r.railMath.minZ);endVector=new Vec(r.railMath.maxX,r.railMath.maxZ);}
  public Rail getRail(){return rail;}
 }
 public static class Vec {public final double x,z;public Vec(double x,double z){this.x=x;this.z=z;}}
}
''',
'RailSpatialHotpathTest.java': '''
import org.mtr.mod.client.*;
import org.mtr.mod.render.strata.StrataRailEngine;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.core.data.Rail;
import java.util.*;
public class RailSpatialHotpathTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static List<MinecraftClientData.RailWrapper> entries(Iterable<MinecraftClientData.RailWrapper> rails){
  List<MinecraftClientData.RailWrapper> all=new ArrayList<>();for(var r:rails)all.add(r);return all;
 }
 public static void main(String[] args){
  MinecraftClientData d=MinecraftClientData.getInstance();
  for(int x=-256;x<=256;x+=64)for(int z=-256;z<=256;z+=64)
   d.railWrapperList.put(x+":"+z,new MinecraftClientData.RailWrapper(new Rail(x,z)));
  d.railSpatialIndex.rebuild(d.railWrapperList.values());
  var first=StrataRailEngine.candidates(new Vector3d(0,0));
  var next=StrataRailEngine.candidates(new Vector3d(0.01,0.02));
  ok(first==next,"tiny camera movement must reuse spatial index list");
  for(int x=-250;x<=250;x+=19)for(int z=-250;z<=250;z+=23){
   var set=entries(StrataRailEngine.candidates(new Vector3d(x,z)));
   for(var rail:d.railWrapperList.values()){
    var box=rail.getRail().railMath;
    if(box.maxX>=x-96&&box.minX<=x+96&&box.maxZ>=z-96&&box.minZ<=z+96)
     ok(set.contains(rail),"lost a potentially visible rail at "+x+","+z);
   }
  }
  var inserted=new MinecraftClientData.RailWrapper(new Rail(5,5));
  d.railWrapperList.put("updated",inserted);
  d.railSpatialIndex.rebuild(d.railWrapperList.values());d.networkRevision++;
  ok(entries(StrataRailEngine.candidates(new Vector3d(0,0))).contains(inserted),"network rebuild updates candidates");
  System.out.println("PASS: stable broadphase cache across camera motion; no missing visible rails; network invalidation");
 }
}
'''
}
with TemporaryDirectory(prefix="sillon-rail-hotpath-") as dirname:
    tmp=Path(dirname)
    for path,source in STUBS.items():
        dest=tmp/path
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(source)
    subprocess.run(["javac","-d",dirname,str(ENGINE),str(SPATIAL),*map(str,tmp.rglob("*.java"))],check=True)
    subprocess.run(["java","-cp",dirname,"RailSpatialHotpathTest"],check=True)
