package dev.igntv.mtrengine;

/**
 * Stable boundary between upstream MTR (Mixins) and an eventual detached
 * renderer. This prototype observes pass boundaries but never redirects draws.
 *
 * The fork's GPU region cache must be extracted separately and only enabled
 * after comparing pixels, shaders, shadows, rail editing and performance.
 */
public final class MtrRenderBridge {
    private static final boolean DIAGNOSTICS=Boolean.getBoolean("mtr.engine.addon.diagnostics");
    private static final ThreadLocal<State> STATE=ThreadLocal.withInitial(State::new);
    private static final class State {
        long start;
        long railsStart;
        long railsNs;
        int railsPasses;
        long frames;
        long lastReport;
    }
    private MtrRenderBridge(){}

    public static void onFrameStart() {
        State state=STATE.get();
        state.start=System.nanoTime();
        state.railsNs=0;
        state.railsPasses=0;
    }

    public static void onRailStart() {
        if(!DIAGNOSTICS)return;
        STATE.get().railsStart=System.nanoTime();
    }

    public static void onRailEnd() {
        if(!DIAGNOSTICS)return;
        State state=STATE.get();
        if(state.railsStart!=0){
            state.railsNs+=System.nanoTime()-state.railsStart;
            state.railsStart=0;
            state.railsPasses++;
        }
    }

    public static void onFrameEnd() {
        if(!DIAGNOSTICS)return;
        State state=STATE.get();
        state.frames++;
        long now=System.nanoTime();
        if(now-state.lastReport>=10_000_000_000L){
            // This reports CPU wall time only; it is not GPU timing or FPS.
            MtrEngineForge.LOGGER.info("MTR Engine addon hook: frames={}, rails-passes-last-frame={}, rails-cpu-ms-last-frame={}",
                state.frames,state.railsPasses,state.railsNs/1_000_000.0);
            state.frames=0;
            state.lastReport=now;
        }
    }
}
