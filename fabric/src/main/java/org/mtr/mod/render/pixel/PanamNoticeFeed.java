package org.mtr.mod.render.pixel;

import java.util.*;

/** Fair network rotation with a bounded eight-notice raster atlas. */
public final class PanamNoticeFeed {
    private List<PanamNotice> source=List.of(),urgent=List.of(),normal=List.of(),window=List.of();
    private int urgentCursor,normalCursor,urgentShown,normalShown;
    private long windowAt,windowDuration;

    public List<PanamNotice> window(List<PanamNotice> candidates,long now){
        LinkedHashMap<PanamNotice,PanamNotice> unique=new LinkedHashMap<>();
        for(PanamNotice n:candidates){
            if(n.message.isEmpty())continue;
            PanamNotice key=n.withPriority(0),old=unique.get(key);
            if(old==null||old.priority<n.priority)unique.put(key,n);
        }
        List<PanamNotice> next=List.copyOf(unique.values());
        if(!next.equals(source)){
            source=next;urgent=new ArrayList<>();normal=new ArrayList<>();
            for(PanamNotice n:source)(n.priority>=3?urgent:normal).add(n);
            urgentCursor=normalCursor=0;windowAt=now;select();
        }
        if(window.isEmpty())return window;
        int jumps=0;
        while(now-windowAt>=windowDuration&&jumps++<128){
            windowAt+=windowDuration;
            urgentCursor=advance(urgentCursor,urgentShown,urgent.size());
            normalCursor=advance(normalCursor,normalShown,normal.size());
            select();
        }
        if(jumps>=128||now<windowAt)windowAt=now;
        return window;
    }
    private static int advance(int cursor,int count,int size){return size==0?0:(cursor+count)%size;}
    private void select(){
        urgentShown=Math.min(normal.isEmpty()?8:4,urgent.size());
        normalShown=Math.min(8-urgentShown,normal.size());
        ArrayList<PanamNotice> selected=new ArrayList<>();
        for(int i=0;i<urgentShown;i++)selected.add(urgent.get((urgentCursor+i)%urgent.size()));
        for(int i=0;i<normalShown;i++)selected.add(normal.get((normalCursor+i)%normal.size()));
        window=List.copyOf(selected);windowDuration=0;
        // Match Timeline's critical/regular interleave. Complete a whole cycle
        // before moving the window, including every critical item selected.
        if(urgentShown>0&&normalShown>0){
            int rounds=Math.max(urgentShown,normalShown);
            for(int i=0;i<rounds;i++)windowDuration+=12000+duration(window.get(urgentShown+i%normalShown));
        }else for(PanamNotice n:window)windowDuration+=duration(n);
        windowDuration=Math.max(1,windowDuration);
    }
    private static long duration(PanamNotice n){return n.priority>=3?12000:n.priority>=1?6500:4500;}
}
