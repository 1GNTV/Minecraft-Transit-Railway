#!/usr/bin/env python3
"""Exercise the production NEXT frametime math with a real Java source launcher.

No Minecraft client/GPU here: FPS measurement still requires a controlled world.
"""
from pathlib import Path
import subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
java=(ROOT/"fabric/src/main/java/org/mtr/mod/render/NextFrameBenchmark.java").read_text()
main=(ROOT/"fabric/src/main/java/org/mtr/mod/render/MainRenderer.java").read_text()
assert "NextFrameBenchmark.sample(System.nanoTime())" in main
assert "if (OptimizedRenderer.renderingShadows())" in main
java=java.replace("package org.mtr.mod.render;", "").replace("import java.util.Arrays;", "").replace("Arrays.", "java.util.Arrays.").replace("public final class NextFrameBenchmark", "final class NextFrameBenchmark")
harness=r"""
public class NextFrameBenchmarkCheck {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
  long now=1_000_000_000L;
  NextFrameBenchmark.reset();
  check(NextFrameBenchmark.sample(now)==null,"first frame ignored");
  NextFrameBenchmark.Result result=null;
  for(int i=0;i<900&&result==null;i++){
   now+=16_666_667L;
   result=NextFrameBenchmark.sample(now);
  }
  check(result!=null&&result.frames()>500,"10-second sample window");
  check(Math.abs(result.averageFps()-60)<.01,"average 60 FPS");
  check(Math.abs(result.onePercentLowFps()-60)<.01,"1 percent low 60 FPS");
  check(Math.abs(result.p99Millis()-16.666667)<.01,"p99 frametime");
  NextFrameBenchmark.reset();
  now=1_000_000_000L;
  check(NextFrameBenchmark.sample(now)==null,"reset");
  check(NextFrameBenchmark.sample(now+2_000_000_000L)==null,"pause excluded");
  now+=2_000_000_000L;
  result=null;
  for(int i=0;i<900&&result==null;i++){
   now+=(i%100==0?80_000_000L:16_666_667L);
   result=NextFrameBenchmark.sample(now);
  }
  check(result!=null&&result.onePercentLowFps()<result.averageFps(),"stutters reduce low FPS");
  check(result.p99Millis()>=16,"p99 frametime");
  System.out.println("PASS: NEXT real-client sampler math, p99, low1, pause reset");
 }
}
"""
with tempfile.TemporaryDirectory(prefix="next-frame-benchmark-") as root:
 p=Path(root)/"NextFrameBenchmarkCheck.java"
 p.write_text(harness+"\n"+java,encoding="utf-8")
 subprocess.run(["java","-Dmtr.next.benchmark=true",str(p)],check=True)
