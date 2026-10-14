#!/usr/bin/env python3
"""Compile STRATA's real pose cache and test JCM-friendly correctness."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "fabric/src/main/java/org/mtr/mod/render/strata/StrataVehiclePositionCache.java"
VEHICLE = ROOT / "fabric/src/main/java/org/mtr/mod/data/VehicleExtension.java"

vehicle = VEHICLE.read_text()
for expected in (
    "@Override\n\tpublic ObjectArrayList",
    "getVehicleCarsAndPositions()",
    "super.getVehicleCarsAndPositions()",
    "positionCache.find(revision, progress, backwards, path, cars, network)",
    "PACKED_JCM_POSITIONS ? HelixPositionSnapshot.capture(fresh) : copyPositions(fresh)",
    "new Vector(a.x, a.y, a.z)",
    "new Vector(b.x, b.y, b.z)",
    "finally { railProgress = oldRailProgress; }",
):
    assert expected in vehicle, expected
assert vehicle.count("positionCache.clear();") == 3, "update, simulation, disposal"
assert "MinecraftClientData.getInstance().networkRevision" in vehicle

JAVA = r'''
import org.mtr.mod.render.strata.StrataVehiclePositionCache;
public class StrataPositionTest {
    static void check(boolean yes, String msg) { if(!yes) throw new AssertionError(msg); }
    public static void main(String[] args) {
        var c = new StrataVehiclePositionCache<String>();
        if (args.length > 0) {
            check(!StrataVehiclePositionCache.enabled(), "kill switch");
            c.store(1, 2, false, new Object(), new Object(), 3, "x");
            check(c.find(1, 2, false, null, null, 3) == null, "disabled cache");
            System.out.println("PASS kill switch");
            return;
        }
        check(StrataVehiclePositionCache.enabled(), "enabled by default");
        Object path = new Object(), cars = new Object();
        c.store(8, 10.0, false, path, cars, 5, "plain");
        check("plain".equals(c.find(8,10.0,false,path,cars,5)), "plain");
        c.store(8, 10.5, false, path, cars, 5, "smooth");
        check("smooth".equals(c.find(8,10.5,false,path,cars,5)), "smooth");
        check("plain".equals(c.find(8,10.0,false,path,cars,5)), "plain restored");
        check("smooth".equals(c.find(8,10.5,false,path,cars,5)), "smooth restored");
        check(c.find(9,10.5,false,path,cars,5)==null, "simulate invalidation");
        check(c.find(8,11.0,false,path,cars,5)==null, "rail progress");
        check(c.find(8,10.5,true,path,cars,5)==null, "reversal");
        check(c.find(8,10.5,false,new Object(),cars,5)==null, "route identity");
        check(c.find(8,10.5,false,path,new Object(),5)==null, "cars identity");
        check(c.find(8,10.5,false,path,cars,6)==null, "network revision");
        check("plain".equals(c.find(8,10.0,false,path,cars,5)), "miss must not evict");
        c.clear();
        check(c.find(8,10.0,false,path,cars,5)==null, "clear on update/dispose");
        System.out.println("PASS normal/smoothed pose isolation, all invalidations");
    }
}
'''
with tempfile.TemporaryDirectory(prefix="strata-positions-") as temp:
    folder = Path(temp)
    (folder / "StrataPositionTest.java").write_text(JAVA)
    subprocess.run([shutil.which("javac"), "-d", temp, str(HELPER),
                    str(folder / "StrataPositionTest.java")], check=True)
    subprocess.run([shutil.which("java"), "-cp", temp, "StrataPositionTest"], check=True)
    subprocess.run([shutil.which("java"), "-Dmtr.strata.vehiclePositionCache=false",
                    "-cp", temp, "StrataPositionTest", "disabled"], check=True)
