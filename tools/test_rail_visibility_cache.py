"""Pure-Java regression checks for exact-view rail sample visibility caching."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "fabric/src/main/java/org/mtr/mod/client/RailVisibilityCache.java"

RAIL = """package org.mtr.core.data;
public final class RailMath {
    public interface RenderRail {
        void renderRail(double x1,double z1,double x2,double z2,double x3,double z3,
                        double x4,double z4,double y1,double y2);
    }
}
"""
GEOMETRY = """package org.mtr.mod.client;
import org.mtr.core.data.RailMath;
public final class RailGeometryCache {
    public int calls;
    public void render(RailMath math,RailMath.RenderRail draw,double interval,float r1,float r2) {
        calls++;
        for(int i=-10;i<10;i++) {
            double x=i*25;
            draw.renderRail(x,0,x+1,0,x+1,1,x,1,0,0);
        }
    }
}
"""
TEST = """import org.mtr.mod.client.*;
import org.mtr.core.data.RailMath;
public final class Verify {
  static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
  static void test(){
    RailVisibilityCache visible=new RailVisibilityCache();
    RailGeometryCache geometry=new RailGeometryCache();
    RailMath rail=new RailMath();
    int[] drawn={0};
    RailMath.RenderRail draw=(a,b,c,d,e,f,g,h,i,j)->drawn[0]++;
    visible.render(rail,geometry,draw,1,.1f,.2f,0,0,0,192,0,0,1);
    int first=drawn[0];
    check(first>0&&first<20,"filtered by camera distance");
    check(geometry.calls==1,"one sample walk");
    drawn[0]=0;
    visible.render(rail,geometry,draw,1,.1f,.2f,0,0,0,192,0,0,1);
    check(drawn[0]==first&&geometry.calls==1&&visible.hits()==1,"stable view hit");
    visible.render(rail,geometry,draw,1,.1f,.2f,10,0,0,192,0,0,1);
    check(geometry.calls==2,"camera movement invalidates");
    visible.render(rail,geometry,draw,1,.1f,.2f,10,0,0,192,1,0,0);
    check(geometry.calls==3,"camera orientation invalidates");
    visible.render(rail,geometry,draw,1,.1f,.2f,10,0,0,96,1,0,0);
    check(geometry.calls==4,"view distance invalidates");
    visible.render(rail,geometry,draw,1,.1f,.3f,10,0,0,96,1,0,0);
    check(geometry.calls==5,"sample key invalidates");
    visible.clear();
    visible.render(rail,geometry,draw,1,.1f,.3f,10,0,0,96,1,0,0);
    check(geometry.calls==6,"network/resource reset invalidates");
    System.out.println("PASS: stable view, camera, projection, render distance, sampling and reset");
  }
  public static void main(String[] args){test();}
}
"""
with TemporaryDirectory() as d:
    root = Path(d)
    for name, content in {
        "org/mtr/core/data/RailMath.java": RAIL,
        "org/mtr/mod/client/RailGeometryCache.java": GEOMETRY,
        "Verify.java": TEST,
    }.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    subprocess.run(
        ["javac", "-d", str(root),
         str(SOURCE), str(root/"org/mtr/core/data/RailMath.java"),
         str(root/"org/mtr/mod/client/RailGeometryCache.java"),
         str(root/"Verify.java")], check=True
    )
    subprocess.run(["java", "-cp", str(root), "Verify"], check=True)
