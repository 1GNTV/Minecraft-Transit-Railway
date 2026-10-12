package dev.igntv.mtrengine.renderer;

/** Only world callbacks own this scope; GUI and arbitrary addon rendering stay immediate. */
public final class WorldTextBatch {
    private static final boolean ENABLED = MtrRailModelBuilds.ENABLED && !"false".equalsIgnoreCase(System.getProperty("mtr.next.textBatch", "true"));
    private static Runnable flush = () -> {};
    private static int depth;
    private WorldTextBatch() {}
    public static boolean active() { return ENABLED && depth > 0; }
    public static void register(Runnable backend) { flush = backend; }
    public static void begin() { boundary(); depth++; }
    public static void end() { try { boundary(); } finally { if (depth > 0) depth--; } }
    public static void boundary() { flush.run(); }
}
