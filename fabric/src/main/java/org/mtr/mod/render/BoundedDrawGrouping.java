package org.mtr.mod.render;
import java.util.*;
import java.util.function.*;

/** Reorder only draws whose conservative screen rectangles cannot touch a crossed draw. */
public final class BoundedDrawGrouping {
    public record Rect(double x0,double y0,double x1,double y1){
        public boolean disjoint(Rect other){return other!=null && (x1<other.x0 || other.x1<x0 || y1<other.y0 || other.y1<y0);}
    }
    private BoundedDrawGrouping(){}
    public static <T> List<T> reorder(List<T> original,BiPredicate<T,T> same,Function<T,Rect> bounds,int window){
        if(original.size()<4)return original;
        List<T> result=null;Map<T,Rect> rectangles=new IdentityHashMap<>();Set<T> missing=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=0;i<original.size()-1;i++){
            List<T> current=result==null?original:result;T first=current.get(i);int write=i+1,limit=Math.min(current.size(),i+window);
            for(int j=write;j<limit;j++){
                T candidate=current.get(j);if(!same.test(first,candidate))continue;
                if(j==write){write++;continue;}
                Rect rectangle=rect(candidate,bounds,rectangles,missing);if(rectangle==null)continue;
                boolean safe=true;for(int crossed=write;crossed<j;crossed++)if(!rectangle.disjoint(rect(current.get(crossed),bounds,rectangles,missing))){safe=false;break;}
                if(!safe)continue;
                if(result==null){result=new ArrayList<>(original);current=result;}
                for(int k=j;k>write;k--)result.set(k,result.get(k-1));result.set(write++,candidate);
            }
            i=write-1;
        }
        return result==null?original:result;
    }
    private static <T> Rect rect(T value,Function<T,Rect> bounds,Map<T,Rect> cache,Set<T> missing){if(missing.contains(value))return null;Rect result=cache.get(value);if(result==null){result=bounds.apply(value);if(result==null)missing.add(value);else cache.put(value,result);}return result;}
}
