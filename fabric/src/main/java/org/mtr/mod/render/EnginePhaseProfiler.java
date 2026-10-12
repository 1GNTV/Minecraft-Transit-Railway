package org.mtr.mod.render;

import org.mtr.mod.Init;

/**
 * Non-intrusive render-thread wall timings, split by main and shader shadow
 * passes. Spark samples inclusive stacks, so without these measurements it
 * cannot distinguish long GPU-driver waits from region construction.
 *
 * -Dmtr.engine.phaseMetrics=true reports once every 10 seconds.
 * This is CPU wall time, NOT the asynchronous GPU timer query result.
 */
public final class EnginePhaseProfiler {
    public enum Phase { VEHICLES, RAILS, REGION_FLUSH, BUILD_QUEUES, SUBMIT }
    private static final boolean ENABLED=Boolean.getBoolean("mtr.engine.phaseMetrics");
    private static final long[][] SUM=new long[2][Phase.values().length];
    private static final long[] PASSES=new long[2];
    private static long lastReport=System.nanoTime();
    private EnginePhaseProfiler(){}
    public static long begin() { return ENABLED ? System.nanoTime() : 0L; }
    public static long mark(long start, boolean shadow, Phase phase) {
        if(!ENABLED)return 0;
        long now=System.nanoTime();
        SUM[shadow?1:0][phase.ordinal()]+=now-start;
        return now;
    }
    public static void end(boolean shadow) {
        if(!ENABLED)return;
        PASSES[shadow?1:0]++;
        long now=System.nanoTime();
        if(now-lastReport<10_000_000_000L)return;
        lastReport=now;
        for(int p=0;p<2;p++){
            long count=PASSES[p];if(count==0)continue;
            for(Phase phase:Phase.values()){
                long nanos=SUM[p][phase.ordinal()];
                Init.LOGGER.info("MTR Engine CPU phase: shadows={}, phase={}, passes={}, avg-ms={}, total-ms={}",
                    p==1,phase,count,nanos/(double)count/1_000_000d,nanos/1_000_000d);
                SUM[p][phase.ordinal()]=0;
            }
            PASSES[p]=0;
        }
    }
}
