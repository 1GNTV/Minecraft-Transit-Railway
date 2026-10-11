package org.mtr.mod.render;
import java.util.*;

/** Stable first-fit placement: visibility/light changes cannot reshuffle already compiled pages. */
public final class RegionPageLayout<L> {
    private record Slot(int page,int size) {}
    private final Map<L,Slot> slots=new HashMap<>();
    private final List<Integer> sizes=new ArrayList<>();
    private final int capacity;
    public RegionPageLayout(int capacity){this.capacity=capacity;}
    public int page(L location,int vertices) {
        if(vertices<=0 || vertices>capacity)throw new IllegalArgumentException("Invalid page contribution");
        Slot old=slots.get(location);
        if(old!=null && old.size==vertices)return old.page;
        if(old!=null)sizes.set(old.page,sizes.get(old.page)-old.size);
        int page=0;while(page<sizes.size() && sizes.get(page)+vertices>capacity)page++;
        if(page==sizes.size())sizes.add(0);
        sizes.set(page,sizes.get(page)+vertices);slots.put(location,new Slot(page,vertices));return page;
    }
    public int locations(){return slots.size();}
}
