#!/usr/bin/env python3
"""Compile the production stopped-train door cache and prove REAL cross-frame reuse.

The counter models the expensive clientWorld.getBlockState calls in scanDoorArea:
if a lookup hits, the loop is not entered. Test 300 render passes, multiple
vehicles, pose edits, door identity, world change, TTL and PSD animation updates.
"""
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "fabric/src/main/java/org/mtr/mod/render/DoorDetectionCache.java"
RENDERER = ROOT / "fabric/src/main/java/org/mtr/mod/render/RenderVehicleHelper.java"
VEHICLES = ROOT / "fabric/src/main/java/org/mtr/mod/render/RenderVehicles.java"
JAVA = r"""
import org.mtr.mod.render.DoorDetectionCache;
public class DoorScanTest {
    static class Scan { final boolean platform; final int[] doorEntities;
        Scan(boolean p,int[] entities){platform=p;doorEntities=entities;} }
    static int reads, animations;
    static final DoorDetectionCache<Scan> CACHE=new DoorDetectionCache<>();
    static final Object WORLD=new Object(), WORLD2=new Object();
    static final Object TRAIN=new Object(), TRAIN2=new Object(), BOX=new Object(), BOX2=new Object();
    static void ok(boolean test,String message){if(!test)throw new AssertionError(message);}
    static Scan render(Object world,Object vehicle,int car,int door,Object box,double x,double yaw,double amount) {
        Scan scan=CACHE.lookup(world,vehicle,car,door,box,x,64,0,yaw,0);
        if(scan==null){
            reads+=240; // Equivalent of the full 3D platform block search.
            scan=new Scan(true,new int[]{1,2});
            CACHE.remember(world,vehicle,car,door,box,x,64,0,yaw,0,scan);
        }
        // This intentionally mirrors the live PSD update OUTSIDE the cache hit/miss.
        for(int id:scan.doorEntities)animations++;
        return scan;
    }
    public static void main(String[]args) {
        CACHE.beginFrame();
        Scan original=render(WORLD,TRAIN,0,0,BOX,1,0,0);
        for(int frame=0;frame<100;frame++){
            if(frame!=0)CACHE.beginFrame();
            for(int pass=0;pass<3;pass++) {
                Scan reused=render(WORLD,TRAIN,0,0,BOX,1,0,pass/2.0);
                if(frame < DoorDetectionCache.REFRESH_FRAMES)ok(reused==original,"same pose/frame reused");
            }
        }
        ok(reads==5*240,"100 frames x 3 shadow/main passes require exactly five scans: "+reads);
        ok(CACHE.hits()==295,"cache must physically reuse results over frames and passes");
        ok(animations==602,"PSD animation side effects execute on all 301 passes");

        long before=CACHE.scans();
        render(WORLD,TRAIN,0,0,BOX,1.00001,0,0);
        ok(CACHE.scans()==before+1,"any train movement invalidates exact pose");
        render(WORLD,TRAIN,0,0,BOX,1.00001,1,0);
        ok(CACHE.scans()==before+2,"yaw changes invalidate");
        render(WORLD,TRAIN,0,0,BOX2,1.00001,1,0);
        ok(CACHE.scans()==before+3,"replaced doorway model invalidates");
        render(WORLD,TRAIN,0,1,BOX2,1.00001,1,0);
        ok(CACHE.scans()==before+4,"different door has separate detection");
        render(WORLD,TRAIN,1,1,BOX2,1.00001,1,0);
        ok(CACHE.scans()==before+5,"different car has separate detection");
        render(WORLD,TRAIN2,1,1,BOX2,1.00001,1,0);
        ok(CACHE.scans()==before+6,"different stopped train has separate detection");

        render(WORLD2,TRAIN,0,0,BOX,1,0,0);
        ok(CACHE.scans()==before+7,"world/dimension swap invalidates cached platform");
        ok(CACHE.vehicleCount()==1,"world swap removes old trains");

        CACHE.clear();
        ok(CACHE.vehicleCount()==0,"resource/world cleanup");
        render(WORLD2,TRAIN,0,0,BOX,1,0,0);
        ok(CACHE.scans()==before+8,"cleared cache performs new full scan");
        System.out.println("PASS: 300 draw passes -> 5 real scans, PSD live, 20-frame TTL, exact motion, multiple cars/trains, world cleanup");
    }
}
"""

def main():
    helper = RENDERER.read_text(encoding="utf-8")
    vehicles = VEHICLES.read_text(encoding="utf-8")
    assert "DOOR_SCANS.lookup(world, vehicle, carNumber, doorNumber" in helper, "cached lookup not wired"
    assert "if (scan == null)" in helper and "scan = scanDoorArea(" in helper, "cache hits must skip scan"
    assert "for (BlockPos screenDoor : scan.screenDoors())" in helper, "PSD side effects must run even on hits"
    assert helper.index("for (BlockPos screenDoor : scan.screenDoors())") < helper.index("private static DoorScan scanDoorArea"), "PSD updates must happen outside scanner"
    assert helper.count("clientWorld.getBlockState(") == 1, "only one getBlockState in guarded scan loop"
    assert "RenderVehicleHelper.beginFrame()" in vehicles, "frame hook missing"
    assert "vehicle, carNumber, doorIndex" in vehicles, "production vehicle/car/door identity not propagated"
    assert "doorway ->" not in vehicles[vehicles.index("openDoorways = new ObjectArrayList<>();",vehicles.index("vehicleResourceCache.doorways")):vehicles.index("final double oscillationAmount")], "per-door index must be used"

    with TemporaryDirectory(prefix="glacier-doors-") as folder:
        path = Path(folder) / "DoorScanTest.java"
        path.write_text(JAVA,encoding="utf-8")
        subprocess.run(["javac","-d",folder,str(SOURCE),str(path)],check=True)
        subprocess.run(["java","-cp",folder,"DoorScanTest"],check=True)

if __name__=="__main__":main()
