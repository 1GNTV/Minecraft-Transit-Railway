package org.mtr.mod.render;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL33;
import org.mtr.mapping.render.object.VertexArray;
import java.nio.IntBuffer;
import java.util.*;

/** CPU selection of index spans; camera motion never rewrites persistent vertex/index geometry. */
public final class RegionVertexRanges {
    public record Span(int first,int count) {}
    private static final Map<VertexArray,Ranges> ACTIVE=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Ranges {
        IntBuffer counts;PointerBuffer offsets;int indices;
        Ranges(int capacity){counts=BufferUtils.createIntBuffer(capacity);offsets=BufferUtils.createPointerBuffer(capacity);}
    }
    private RegionVertexRanges() {}
    public static void select(VertexArray array,List<Span> spans){
        Ranges ranges=ACTIVE.get(array);if(ranges==null || ranges.counts.capacity()<spans.size()){ranges=new Ranges(Math.max(16,spans.size()));ACTIVE.put(array,ranges);}
        ranges.counts.clear();ranges.offsets.clear();ranges.indices=0;
        int bytes=array.indexBuffer.indexType==GL33.GL_UNSIGNED_BYTE?1:array.indexBuffer.indexType==GL33.GL_UNSIGNED_SHORT?2:4;
        for(Span span:spans){ranges.counts.put(span.count);ranges.offsets.put((long)span.first*bytes);ranges.indices+=span.count;}
        ranges.counts.flip();ranges.offsets.flip();
    }
    public static void draw(VertexArray array,int mode,int count,int type,long offset){
        Ranges ranges=ACTIVE.get(array);
        if(ranges==null){GL33.glDrawElements(mode,count,type,offset);return;}
        if(ranges.counts.hasRemaining())GL33.glMultiDrawElements(mode,ranges.counts,type,ranges.offsets);
    }
    public static boolean contains(VertexArray array){return ACTIVE.containsKey(array);}
    public static int indexCount(VertexArray array){Ranges ranges=ACTIVE.get(array);return ranges==null?array.indexBuffer.getVertexCount():ranges.indices;}
    public static void remove(VertexArray array){ACTIVE.remove(array);}
}
