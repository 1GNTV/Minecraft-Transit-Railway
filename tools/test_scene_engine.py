"""Exercise the actual engine scene-plan cache without Minecraft or OpenGL."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "fabric/src/main/java/org/mtr/mod/render/ScenePlanCache.java"
TEST = r"""
import org.mtr.mod.render.ScenePlanCache;
import java.util.concurrent.atomic.AtomicInteger;

public class ScenePlanTest {
    record Revision(int geometry, int lighting, int resourceGeneration) {}
    record Plan(int token) {}
    static void check(boolean ok, String context) {
        if (!ok) throw new AssertionError(context);
    }
    public static void main(String[] args) {
        var builds = new AtomicInteger();
        var cache = new ScenePlanCache<String, Revision, Plan>(2);
        Revision first = new Revision(1, 12, 1);
        Plan a = cache.resolve("platform", first, () -> new Plan(builds.incrementAndGet()));
        for (int i = 0; i < 1000; i++) {
            Plan same = cache.resolve("platform", new Revision(1, 12, 1),
                    () -> { throw new AssertionError("unchanged scene rebuilt"); });
            check(same == a, "stable scene must preserve plan identity");
        }
        check(cache.hits() == 1000 && cache.misses() == 1, "cache statistics");
        Plan relit = cache.resolve("platform", new Revision(1, 13, 1),
                () -> new Plan(builds.incrementAndGet()));
        check(relit != a && builds.get() == 2, "lighting revision invalidates");
        Plan reload = cache.resolve("platform", new Revision(1, 13, 2),
                () -> new Plan(builds.incrementAndGet()));
        check(reload != relit, "resource generation invalidates");
        check(cache.resolve("platform", new Revision(1, 13, 2),
                () -> {throw new AssertionError("unexpected rebuild");}) == reload,
                "stable updated revision");
        try {
            cache.resolve("platform", new Revision(2, 13, 2), () -> {
                throw new IllegalArgumentException("failed scene rebuild");
            });
            throw new AssertionError("exception not propagated");
        } catch (IllegalArgumentException expected) { }
        check(cache.resolve("platform", new Revision(1, 13, 2),
                () -> {throw new AssertionError("failed rebuild replaced valid plan");}) == reload,
                "failed construction leaves last valid plan intact");
        cache.resolve("rail-a", first, () -> new Plan(builds.incrementAndGet()));
        cache.resolve("rail-b", first, () -> new Plan(builds.incrementAndGet()));
        check(cache.size() == 2, "bounded scene metadata");
        check(cache.resolve("platform", new Revision(1, 13, 2),
                () -> new Plan(builds.incrementAndGet())) != reload,
                "LRU entry must be rebuilt after eviction");
        cache.clear();
        check(cache.size() == 0 && cache.hits() == 0 && cache.misses() == 0,
                "world and resource reload clear");
        System.out.println("PASS: stable scenes, light/reload revisions, failure safety, eviction, cleanup");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="mtr-scene-engine-") as dirname:
    root = Path(dirname)
    test = root / "ScenePlanTest.java"
    test.write_text(TEST, encoding="utf-8")
    subprocess.run(["javac", "-d", dirname, str(HELPER), str(test)], check=True)
    subprocess.run(["java", "-cp", dirname, "ScenePlanTest"], check=True)
