#!/usr/bin/env python3
"""Source and Java-level safety tests for the Sillon shadow distance budget.

Tests real helper Java logic, not in-game FPS or the Oculus shader renderer.
"""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
pkg=root/"fabric/src/main/java/org/mtr/mod/render"
rail=(pkg/"strata/StrataRailEngine.java").read_text(encoding="utf-8")
train=(pkg/"TrainVisibility.java").read_text(encoding="utf-8")
main=(pkg/"MainRenderer.java").read_text(encoding="utf-8")

assert "if(shadowPass && cameraValid &&" in rail
assert "ShadowDistanceBudget.visible(" in rail
assert "rail.railMath.minX,rail.railMath.maxX" in rail
assert "SHADOW_RAIL_DISTANCE=Math.max(0,Integer.getInteger" in rail
assert "ShadowDistanceBudget.trainRadius(distance,SHADOW_TRAIN_DISTANCE)" in train
assert "mtr.sillon.shadowTrainDistance" in train
assert "if (shadowPass)" not in main or "OptimizedRenderer.renderingShadows()" in main
assert "RenderVehicles.render(millisElapsed, cameraShakeOffset)" in main
assert "RenderRails.render()" in main

java=r"""
import org.mtr.mod.render.ShadowDistanceBudget;
public final class ShadowBudgetSafety {
    static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        // Entire track is beyond budget.
        check(!ShadowDistanceBudget.visible(0,0,140,160,-2,2,128),
              "reject a distant rail shadow");
        // A long crossing track must remain visible.
        check(ShadowDistanceBudget.visible(0,0,-200,200,-1,1,128),
              "retain crossing rail");
        check(ShadowDistanceBudget.visible(0,0,100,120,100,120,160),
              "retain a corner within radius");
        check(!ShadowDistanceBudget.visible(0,0,100,120,100,120,128),
              "reject a corner outside radius");
        check(ShadowDistanceBudget.visible(0,0,140,160,-2,2,0),
              "zero radius restores all casters");
        check(ShadowDistanceBudget.visible(Double.NaN,0,140,160,-2,2,128),
              "unknown camera fails open");
        check(ShadowDistanceBudget.visible(0,0,200,-200,-2,2,128),
              "invalid addon bounds fail open");
        check(ShadowDistanceBudget.trainRadius(192,160)==160,
              "train budget caps distant casters");
        check(ShadowDistanceBudget.trainRadius(192,0)==192,
              "train rollback restores legacy distance");
        System.out.println("PASS: shadow budgets preserve near/crossing casters and have rollback");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="sillon-shadows-") as tmp:
    p=Path(tmp)
    test=p/"ShadowBudgetSafety.java"
    test.write_text(java,encoding="utf-8")
    subprocess.run(["javac","--release","17","-d",tmp,str(pkg/"ShadowDistanceBudget.java"),str(test)],check=True)
    subprocess.run(["java","-cp",tmp,"ShadowBudgetSafety"],check=True)
print("PASS: MTR/JCM entry point signatures remain untouched (source guard)")
