package org.mtr.mod.render;

import java.util.Arrays;

/**
 * Optional client frame-time sampler for reproducible Sillon / stock MTR tests.
 * No GL queries, object allocations or sorting on individual render frames.
 * Sample only from the regular main render pass, never shadow cascades.
 */
public final class NextFrameBenchmark {
    private static final boolean ENABLED=Boolean.getBoolean("mtr.next.benchmark");
    private static final long WINDOW_NS=10_000_000_000L;
    private static final long PAUSE_NS=1_000_000_000L;
    private static final long[] FRAMES=new long[2400];
    private static long last,windowStart,total;
    private static int size,index;

    public record Result(int frames,double averageFps,double onePercentLowFps,double p99Millis) {}
    private NextFrameBenchmark(){}

    public static Result sample(long now) {
        if(!ENABLED)return null;
        if(last==0){last=windowStart=now;return null;}
        final long elapsed=now-last;
        last=now;
        if(elapsed<=0||elapsed>PAUSE_NS){resetWindow(now);return null;}
        if(size==FRAMES.length)total-=FRAMES[index];
        else size++;
        FRAMES[index]=elapsed;
        index=(index+1)%FRAMES.length;
        total+=elapsed;
        if(now-windowStart<WINDOW_NS||size<100)return null;
        // The bottom 1% FPS uses the mean of the slowest 1% frame times.
        // Do not mix main-pass timings with GPU shadow cascade durations.
        long[] sorted=Arrays.copyOf(FRAMES,size);
        Arrays.sort(sorted);
        int worstCount=Math.max(1,(int)Math.ceil(size*.01));
        long slowest=0;
        for(int i=size-worstCount;i<size;i++)slowest+=sorted[i];
        Result result=new Result(size,
            1_000_000_000.0*size/total,
            1_000_000_000.0*worstCount/slowest,
            sorted[Math.max(0,(int)Math.ceil(size*.99)-1)]/1_000_000.0);
        resetWindow(now);
        return result;
    }

    private static void resetWindow(long at){
        windowStart=at;
        size=index=0;
        total=0;
    }
    public static void reset(){
        last=0;
        resetWindow(0);
    }
}
