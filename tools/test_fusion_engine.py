#!/usr/bin/env python3
"""Compile and exercise the isolated MTR Engine V4 scene/region caches."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / "fabric/src/main/java/org/mtr/mod/render/ScenePlanCache.java",
    ROOT / "fabric/src/main/java/org/mtr/mod/render/PersistentRegionCache.java",
    ROOT / "fabric/src/main/java/org/mtr/mod/render/FrameMemoizer.java",
]
JAVA = r"""
import org.mtr.mod.render.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FusionSmokeTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static class Handle implements AutoCloseable {
        boolean closed;
        public void close() { closed = true; }
    }
    public static void main(String[] args) {
        ScenePlanCache<String, Integer, Object> plans = new ScenePlanCache<>(4);
        Object a = new Object(), b = new Object();
        check(plans.resolve("a", 1, () -> a) == a, "first scene plan");
        check(plans.resolveLazy("a", v -> v == 1,
            () -> { throw new AssertionError("stable revision allocated"); },
            v -> { throw new AssertionError("stable plan rebuilt"); }) == a, "stable scene plan");
        check(plans.resolve("b", 1, () -> b) == b, "second scene plan");
        check(plans.invalidateWhere(k -> k.equals("a")) == 1, "dirty sector invalidation");
        check(plans.size() == 1, "unrelated scene retained");
        check(plans.resolve("b", 1, () -> { throw new AssertionError("unrelated plan rebuilt"); }) == b,
            "unrelated plan reusable");

        PersistentRegionCache<String, Integer, Integer, Handle> pages =
            new PersistentRegionCache<>(4, 100, 10);
        pages.beginFrame();
        Handle pageA = pages.get("a", List.of(1), v -> v, v -> 3, v -> new Handle());
        check(pageA != null, "first retained page");
        pages.beginFrame();
        check(pages.get("a", List.of(1), v -> v, v -> 3, v -> {
            throw new AssertionError("unmodified page uploaded again");
        }) == pageA, "GPU retained page hit");
        Handle pageB = pages.get("b", List.of(2), v -> v, v -> 3, v -> new Handle());
        check(pageB != null, "independent region page");
        check(pages.invalidateWhere(k -> k.equals("a")) == 1, "GPU sector invalidation");
        check(pageA.closed, "dirty GPU page released");
        check(!pageB.closed, "unrelated GPU page survived");
        pages.close();
        check(pageB.closed, "all pages released on world unload");

        FrameMemoizer<Object, Object> memo = new FrameMemoizer<>(4);
        Object key = new Object(), value = new Object();
        memo.beginFrame();
        check(memo.get(key, 1, () -> value) == value, "first train transform");
        check(memo.get(key, 1, () -> {
            throw new AssertionError("same-frame train transform rebuilt");
        }) == value, "reuse transform across shadow passes");
        memo.endFrame();
        memo.beginFrame();
        check(memo.get(key, 1, Object::new) != value, "no transform retained across frames");
        System.out.println("PASS: targeted CPU/GPU sector invalidation and frame-local train transforms");
    }
}
"""
def main():
    with TemporaryDirectory(prefix="mtr-fusion-") as temp:
        out = Path(temp)
        java = out / "FusionSmokeTest.java"
        java.write_text(JAVA, encoding="utf-8")
        subprocess.run(["javac", "-d", str(out), *map(str, SOURCES), str(java)], check=True)
        subprocess.run(["java", "-cp", str(out), "FusionSmokeTest"], check=True)

    vehicle = (ROOT / "fabric/src/main/java/org/mtr/mod/render/RenderVehicles.java").read_text()
    batcher = (ROOT / "fabric/src/main/java/org/mtr/mod/render/RailSectorBatcher.java").read_text()
    regions = (ROOT / "fabric/src/main/java/org/mtr/mod/render/StaticRegionRenderer.java").read_text()
    assert "absoluteTransform(absoluteBogiePositionAndRotation, 0)" in vehicle
    assert "absoluteTransform(vehicleCarDetails.right().right(), oscillationAmount)" in vehicle
    assert "offsetVector == null" in vehicle
    assert "appliedNetworkRevision" in batcher and "REGIONS.clear()" in batcher
    assert "invalidateSector(int x,int y,int z)" in regions
    print("PASS: V4 E1+E2 wiring checks")

if __name__ == "__main__":
    main()
