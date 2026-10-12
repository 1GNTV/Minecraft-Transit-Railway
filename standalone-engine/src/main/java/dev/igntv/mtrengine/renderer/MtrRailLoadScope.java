package dev.igntv.mtrengine.renderer;

/**
 * During original MTR RailResource lazy loading, redirect ONLY rail model
 * factories to Queue Hotpath V2's retained mesh builder.
 * Fully reentrant: any nested fallback factory uses vanilla MTR.
 */
public final class MtrRailLoadScope {
    private static final class Frame {int depth;boolean constructing;}
    private static final ThreadLocal<Frame> FRAME=ThreadLocal.withInitial(Frame::new);
    private MtrRailLoadScope(){}
    public static void enter() {
        if(!MtrRailModelBuilds.ENABLED)return;
        FRAME.get().depth++;
    }
    public static void exit() {
        if(!MtrRailModelBuilds.ENABLED)return;
        Frame f=FRAME.get();
        if(f.depth>0)f.depth--;
        if(f.depth==0)f.constructing=false;
    }
    public static boolean beginBuild() {
        if(!MtrRailModelBuilds.ENABLED)return false;
        Frame f=FRAME.get();
        if(f.depth==0 || f.constructing)return false;
        f.constructing=true;
        return true;
    }
    public static void endBuild() {
        FRAME.get().constructing=false;
    }
}
