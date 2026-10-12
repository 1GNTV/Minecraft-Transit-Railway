package org.mtr.mod.render;
import java.util.*;
import java.util.function.*;

/** Content membership survives visibility changes; immutable queued resources are pinned for a pass. */
public final class PersistentRegionCache<K,V,L,R extends AutoCloseable> implements AutoCloseable {
    private static final class Entry<V,L,R>{Map<L,V> content=new LinkedHashMap<>();Set<V> compiled=Set.of(),failed=Set.of();R resource;long seen;int vertices;}
    private final Map<K,Entry<V,L,R>> entries=new LinkedHashMap<>(64,.75f,true);
    private final int maxEntries,maxVertices,maxBuildVertices;
    private int vertices,builds;
    private long frame;
    public PersistentRegionCache(int entries,int vertices,int build){maxEntries=entries;maxVertices=vertices;maxBuildVertices=build;}
    public void beginFrame(){frame++;builds=1;}
    public R get(K key,List<V> visible,Function<V,L> location,ToIntFunction<V> size,Function<List<V>,R> builder){
        if(visible.isEmpty())return null;
        Entry<V,L,R> entry=entries.get(key);
        if(entry==null){if(!room(null,1,0))return null;entry=new Entry<>();entries.put(key,entry);}
        // Hot path: compiled geometry ALREADY owns every currently visible
        // selection. There is no new membership to record, so avoid hundreds
        // of LinkedHashMap.put / hash computations per shadow/main pass.
        // A changed model, light, matrix or unseen object fails containsAll
        // and proceeds to the persistent membership update below.
        if(entry.resource!=null && entry.compiled.containsAll(visible)){
            entry.seen=frame;
            return entry.resource;
        }
        // Previous code added the sizes before accounting for duplicate locations:
        // a stable region with >256 visible pieces therefore cleared membership
        // EVERY frame, rebuilding hundreds of map entries for unchanged geometry.
        for(V value:visible)entry.content.put(location.apply(value),value);
        if(entry.content.size()>512){
            // Trim only when the real UNIQUE membership cap is exceeded.
            // Keep this frame's visible set, never leave stale geometry selected.
            entry.content.clear();
            for(V value:visible)entry.content.put(location.apply(value),value);
        }
        if(entry.seen==frame || builds==0)return null;
        List<V> content=List.copyOf(entry.content.values());int count=content.stream().mapToInt(size).sum();
        if(count<=0 || count>maxBuildVertices || count>maxVertices)return null;
        if(!room(entry,0,count-entry.vertices))return null;
        Set<V> signature=Set.copyOf(content);if(entry.failed.equals(signature))return null;
        builds--;
        R replacement;try{replacement=builder.apply(content);}catch(RuntimeException error){entry.failed=signature;throw error;}if(replacement==null){entry.failed=signature;return null;}
        entry.failed=Set.of();
        release(entry);entry.resource=replacement;entry.compiled=Set.copyOf(content);entry.vertices=count;vertices+=count;entry.seen=frame;return replacement;
    }
    private boolean room(Entry<V,L,R> keep,int additional,int vertexDelta){
        var it=entries.values().iterator();
        while((entries.size()+additional>maxEntries || vertices+vertexDelta>maxVertices) && it.hasNext()){
            var victim=it.next();if(victim==keep || victim.seen==frame)continue;release(victim);it.remove();
        }return entries.size()+additional<=maxEntries && vertices+vertexDelta<=maxVertices;
    }
    private void release(Entry<V,L,R> entry){if(entry.resource==null)return;try{entry.resource.close();}catch(Exception e){throw new IllegalStateException(e);}finally{vertices-=entry.vertices;entry.vertices=0;entry.resource=null;}}
    public int residentVertices(){return vertices;}
    @Override public void close(){entries.values().forEach(this::release);entries.clear();vertices=0;}
}
