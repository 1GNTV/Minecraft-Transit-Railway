#!/usr/bin/env python3
"""HELIX P0 + retained submission CPU invariants and actual Java smoothing."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
read=lambda name:(ROOT/name).read_text()
v=read("fabric/src/main/java/org/mtr/mod/data/VehicleExtension.java")
p=read("fabric/src/main/java/org/mtr/mod/data/PersistentVehicleData.java")
s=read("fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java")
r=read("fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java")
c=read("fabric/src/main/java/org/mtr/mod/render/ClassicRailGpuCache.java")
rails=read("fabric/src/main/java/org/mtr/mod/render/RenderRails.java")
vehicles=read("fabric/src/main/java/org/mtr/mod/render/RenderVehicles.java")
helper=ROOT/"fabric/src/main/java/org/mtr/mod/render/helix/HelixMotionSmoother.java"

# P0: render calls are read-only; simulation is the ONLY HELIX updater.
assert "advanceHelixSmoothedRailProgress(railProgress,millisElapsed" in v
assert "getHelixSmoothedRailProgress(railProgress)" in v
assert "HelixMotionSmoother.enabled()" in v
assert "finally { railProgress = oldRailProgress; }" in v
assert p.count("advanceHelixSmoothedRailProgress(")==1
assert "public double getHelixSmoothedRailProgress" in p
assert "return progress-railProgressSmoothingAdjustment;" in p
assert "smoothedRailProgress = progress-railProgressSmoothingAdjustment;" in p

# P0: keep shader lighting fallback available and conservative by default.
assert 'System.getProperty("mtr.engine.classicGpu","false")' in c
assert "ClassicRailGpuCache.queue(" in read("fabric/src/main/java/org/mtr/mod/render/ClassicRailQuadBatch.java")
assert "IDrawing.drawTexture(" in read("fabric/src/main/java/org/mtr/mod/render/ClassicRailQuadBatch.java")
assert "RailLightFrameCache.get(" in rails
assert "mtr.engine.classicGpu" in c

# P0: do not modify MTR add-on Mixin entry points or the actual JCM lambda.
assert "public static void render(long millisElapsed, Vector3d cameraShakeOffset)" in vehicles
assert "vehicleResource.iterateModels(carNumber" in vehicles
assert "StrataVehicleVisibility.push(visualCar)" in vehicles
assert "REGIONS.replay(piece.ticket,clientWorld.data,currentModel,light)" in r

# P1: persistent group-map reuse, ordered equality-based keys, plus rollback.
assert "private LinkedHashMap<Object,Instance> groupFor(Sector sector)" in s
assert "sector.equals(hotSector)" in s
assert "freeGroups.size()<128" in s
assert "groupFor(ticket.sector).put(ticket.denseId,ticket.instance)" in s
assert "groupFor(sector).put(idFor(location),new Instance" in s
assert "releaseGroups();" in s
assert "mtr.helix.stagedGroups" in s
assert "groups.clear();" in s

# P1: each GPU page captures one reusable render callback; no pre-captured camera.
assert "BiConsumer<GraphicsHolder,Vector3d> drawCommand;" in s
assert "Int2ObjectLinkedOpenHashMap<Instance>" in s
assert "ticket.denseId" in s
assert "mtr.helix.denseRails" in s
assert "groupFor(ticket.sector).put(ticket.denseId,ticket.instance)" in s
assert "page.drawCommand==null" in s
assert "MainRenderer.scheduleRender(QueuedRenderLayer.TEXT,page.drawCommand);" in s
assert "holder.translate(sector.x()*16D-offset.getXMapped()" in s
assert "ModelDetailCache.pushDistance(" in s
assert "instance.resource().render(instance.fallback(),instance.light())" in s

JAVA=r'''
import org.mtr.mod.render.helix.HelixMotionSmoother;
public class HelixSmootherContract {
    private static void eq(boolean b,String what) { if(!b)throw new AssertionError(what); }
    public static void main(String[] args) {
        if(args.length>0){
            eq(!HelixMotionSmoother.enabled(),"kill switch");
            System.out.println("PASS HELIX smoothing rollback");
            return;
        }
        eq(HelixMotionSmoother.enabled(),"enabled");
        double left=4.0;
        // Arrival while braking must approach zero smoothly, including exact zero speed.
        for(int i=0;i<180 && left!=0;i++){
            double next=HelixMotionSmoother.remainingCorrection(left,16);
            eq(next>=0 && next<left,"monotonic braking "+i);
            left=next;
        }
        eq(left==0,"settles with no final discontinuity");
        eq(HelixMotionSmoother.remainingCorrection(4,0)==4,"render pass is read-only");
        eq(HelixMotionSmoother.remainingCorrection(4,-1)==4,"invalid delta is safe");
        double one=HelixMotionSmoother.remainingCorrection(4,100);
        double many=4;
        for(int i=0;i<10;i++)many=HelixMotionSmoother.remainingCorrection(many,10);
        eq(Math.abs(one-many)<1e-8,"time step independent");
        eq(HelixMotionSmoother.remainingCorrection(-4,100)>-4,"negative correction converges");
        eq(Double.isNaN(HelixMotionSmoother.remainingCorrection(Double.NaN,100)),"nonfinite input preserved");
        System.out.println("PASS HELIX braking damping, timestep invariance, read-only shadows");
    }
}
'''
with tempfile.TemporaryDirectory(prefix="helix-smoothing-") as tmp:
    dest=Path(tmp)
    (dest/"HelixSmootherContract.java").write_text(JAVA)
    subprocess.run([shutil.which("javac"),"-d",tmp,str(helper),str(dest/"HelixSmootherContract.java")],check=True)
    subprocess.run([shutil.which("java"),"-cp",tmp,"HelixSmootherContract"],check=True)
    subprocess.run([shutil.which("java"),"-Dmtr.helix.stableSmoothing=false","-cp",tmp,
                    "HelixSmootherContract","disabled"],check=True)
# New large opportunities are isolated by flags and tested for source wiring.
vpos=read("fabric/src/main/java/org/mtr/mod/data/VehicleExtension.java")
packed=read("fabric/src/main/java/org/mtr/mod/render/helix/HelixPositionSnapshot.java")
candidates=read("fabric/src/main/java/org/mtr/mod/render/strata/StrataRailEngine.java")
gpu=read("fabric/src/main/java/org/mtr/mod/render/RegionVertexRanges.java")
instances=read("fabric/src/main/java/org/mtr/mod/render/InstancedModelRenderer.java")
assert "HelixPositionSnapshot.capture(fresh)" in vpos
assert "saved instanceof HelixPositionSnapshot packed" in vpos
assert "mtr.helix.packedPositions" in vpos and "return packed.copy()" in vpos
assert "new Vector(values[j], values[j+1], values[j+2])" in packed
assert "candidateRevision == currentData.networkRevision" in candidates
assert "mtr.helix.passCandidates" in candidates
assert "spans = compact(spans)" in gpu and "mtr.helix.coalesceSpans" in gpu
assert "mtr.helix.shadowInstances" in instances
assert "mtr.helix.shadowIndirect" in gpu
print("PASS HELIX large upgrades: dense IDs, shadow-query reuse, packed addon snapshots and GPU batching")
