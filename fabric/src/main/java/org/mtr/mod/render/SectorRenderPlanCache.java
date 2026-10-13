package org.mtr.mod.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Persistent sector membership built from immutable ScenePlans.
 * Reuse happens only when EVERY plan is the exact same object in the same
 * order. This preserves changes to light, models, transforms and membership:
 * each of them creates a new ScenePlan or changes the member list.
 *
 * The renderer supplies an immutable page->selection list as V. Only the
 * lightweight plan identities are checked every pass; per-chunk lists are
 * constructed once, not on every main/Oculus shadow submission.
 */
public final class SectorRenderPlanCache<K,P,V> {
    private static final class Entry<P,V> {
        final List<P> plans;
        final V pages;
        Entry(List<P> plans,V pages){this.plans=plans;this.pages=pages;}
        boolean matches(List<P> current){
            if(plans.size()!=current.size())return false;
            for(int i=0;i<current.size();i++)if(plans.get(i)!=current.get(i))return false;
            return true;
        }
    }
    private final int capacity;
    private final LinkedHashMap<K,Entry<P,V>> sectors;
    private long hits,builds;

    public SectorRenderPlanCache(int capacity){
        if(capacity<1)throw new IllegalArgumentException("capacity");
        this.capacity=capacity;
        sectors=new LinkedHashMap<>(Math.min(capacity,256),.75f,true);
    }
    public V resolve(K key,List<P> plans,Supplier<V> build){
        Objects.requireNonNull(key,"sector");
        Objects.requireNonNull(plans,"plans");
        Objects.requireNonNull(build,"build");
        Entry<P,V> old=sectors.get(key);
        if(old!=null && old.matches(plans)){hits++;return old.pages;}
        V pages=Objects.requireNonNull(build.get(),"sector pages");
        builds++;
        sectors.put(key,new Entry<>(List.copyOf(plans),pages));
        while(sectors.size()>capacity)sectors.remove(sectors.keySet().iterator().next());
        return pages;
    }
    public int invalidateWhere(Predicate<K> predicate){
        int removed=0;
        var it=sectors.keySet().iterator();
        while(it.hasNext())if(predicate.test(it.next())){it.remove();removed++;}
        return removed;
    }
    public void clear(){sectors.clear();}
    public int size(){return sectors.size();}
    public long hits(){return hits;}
    public long builds(){return builds;}
}
