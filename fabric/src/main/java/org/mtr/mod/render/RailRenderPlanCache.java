package org.mtr.mod.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bounded render-thread cache of immutable per-rail submission plans.
 *
 * A cached, visible double[] is stable ONLY while the precise camera and
 * direction are unchanged. Its object identity is the visibility revision.
 * Together with rail geometry identity and resource identity, a cache hit
 * guarantees that the static segment transformations/lighting coordinates
 * need not be reconstructed across main and Oculus shadow passes.
 *
 * Light values are NEVER included in the plan: they are sampled live each
 * frame, just as with upstream MTR. Cached plans are invalidated on rail
 * network updates, resource reloads, and world changes by the caller.
 */
public final class RailRenderPlanCache<V> {
    private static final int MAX_ENTRIES=256;
    private static final int MAX_SEGMENTS=65_536;
    private static final int MAX_PER_ENTRY=4096;

    private static final class Key {
        final Object rail,resource,samples;
        final boolean flip;
        final int hash;
        Key(Object rail,Object resource,double[] samples,boolean flip) {
            this.rail=rail;this.resource=resource;this.samples=samples;this.flip=flip;
            int h=System.identityHashCode(rail);
            h=31*h+System.identityHashCode(resource);
            hash=31*(31*h+System.identityHashCode(samples))+Boolean.hashCode(flip);
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            return other instanceof Key k && rail==k.rail && resource==k.resource && samples==k.samples && flip==k.flip;
        }
    }
    private static final class Entry<V> {
        final V value;
        final int segments;
        Entry(V value,int segments){this.value=value;this.segments=segments;}
    }

    private final LinkedHashMap<Key,Entry<V>> plans=new LinkedHashMap<>(128,.75f,true);
    private int segments;
    private long hits,misses,builds;

    /** Null = oversized entry, caller must fall back to original segment path. */
    public V resolve(Object rail,Object resource,double[] samples,boolean flip,Function<double[],V> build) {
        Objects.requireNonNull(rail,"rail");
        Objects.requireNonNull(resource,"resource");
        Objects.requireNonNull(samples,"samples");
        Objects.requireNonNull(build,"build");
        if(samples.length%10!=0)throw new IllegalArgumentException("expected 10 doubles per segment");
        int count=samples.length/10;
        if(count>MAX_PER_ENTRY){misses++;return null;}
        Key key=new Key(rail,resource,samples,flip);
        Entry<V> cached=plans.get(key);
        if(cached!=null){hits++;return cached.value;}
        misses++;
        V value=Objects.requireNonNull(build.apply(samples),"rail render plan");
        builds++;
        plans.put(key,new Entry<>(value,count));
        segments+=count;
        var it=plans.entrySet().iterator();
        while(plans.size()>MAX_ENTRIES || segments>MAX_SEGMENTS) {
            Map.Entry<Key,Entry<V>> victim=it.next();
            segments-=victim.getValue().segments;
            it.remove();
        }
        return value;
    }
    public void clear(){plans.clear();segments=0;}
    public int size(){return plans.size();}
    public long hits(){return hits;}
    public long misses(){return misses;}
    public long builds(){return builds;}
}
