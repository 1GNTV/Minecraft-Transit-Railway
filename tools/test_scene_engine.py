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
    static final class MutableKey {
        int id;
        MutableKey(int id){this.id=id;}
        MutableKey copy(){return new MutableKey(id);}
        @Override public int hashCode(){return id;}
        @Override public boolean equals(Object o){return o instanceof MutableKey key && key.id==id;}
    }
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
        check(cache.resolveLazy("platform", old -> old.equals(first),
                () -> { throw new AssertionError("stable scene allocated a revision"); },
                rev -> { throw new AssertionError("stable scene recompiled"); }) == a,
                "lazy matching without allocations");
        Plan lazyChanged = cache.resolveLazy("platform", old -> false,
                () -> new Revision(1, 11, 1),
                rev -> new Plan(builds.incrementAndGet()));
        check(lazyChanged != a, "lazy changed scene invalidates");
        a = cache.resolve("platform", first, () -> new Plan(builds.incrementAndGet()));
        check(builds.get() == 3, "lazy and eager rebuild counts");
        Plan relit = cache.resolve("platform", new Revision(1, 13, 1),
                () -> new Plan(builds.incrementAndGet()));
        check(relit != a && builds.get() == 4, "lighting revision invalidates");
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
        // Verify render-thread scratch lookups never enter the persistent
        // HashMap, that stable frames allocate zero persistent copies, and
        // that a mutated scratch key cannot corrupt a cached map entry.
        var fast = new ScenePlanCache<MutableKey, Revision, Plan>(2);
        var scratch = new MutableKey(42);
        var copies = new AtomicInteger();
        Revision hotRevision = new Revision(7, 15, 3);
        Plan hot = fast.resolveLazyLookup(scratch, k -> {
                copies.incrementAndGet(); return k.copy();
            }, old -> false, () -> hotRevision,
            revision -> new Plan(101));
        check(copies.get()==1 && fast.size()==1, "cold miss retains one immutable key");
        for(int i=0;i<10000;i++) {
            check(fast.resolveLazyLookup(scratch, k -> {
                    copies.incrementAndGet(); return k.copy();
                }, old -> old.equals(hotRevision),
                () -> {throw new AssertionError("stable hit must not allocate revision");},
                revision -> {throw new AssertionError("stable hit must not rebuild");})==hot,
                "stable hot-path identity");
        }
        check(copies.get()==1 && fast.hits()==10000,
                "scratch key must not be copied on stable hits");
        scratch.id=99;
        fast.resolveLazyLookup(scratch, MutableKey::copy, old -> false,
                () -> hotRevision, revision -> new Plan(102));
        scratch.id=42;
        check(fast.resolveLazyLookup(scratch, MutableKey::copy,
                old -> old.equals(hotRevision),
                () -> {throw new AssertionError("stored key was mutated");},
                revision -> {throw new AssertionError("stable key evicted");})==hot,
                "mutable scratch must not be stored as key");
        try {
            scratch.id=101;
            fast.resolveLazyLookup(scratch, MutableKey::copy, old -> false,
                    () -> hotRevision,
                    revision -> {throw new IllegalArgumentException("failed build");});
            throw new AssertionError("expected failed build");
        } catch(IllegalArgumentException expected) {}
        check(fast.size()==2, "failed build must not insert permanent key");
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
