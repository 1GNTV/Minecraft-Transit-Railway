package org.mtr.mod.render.helix;

/** A frame-rate-independent correction of server rail progression.
 * Advance once in the client simulation, never in a shader render pass.
 */
public final class HelixMotionSmoother {
    private static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("mtr.helix.stableSmoothing", "true"));
    private HelixMotionSmoother() {}
    public static boolean enabled() { return ENABLED; }
    public static double remainingCorrection(double correction, long elapsedMillis) {
        if(elapsedMillis <= 0 || correction == 0 || !Double.isFinite(correction)) return correction;
        double next = correction * Math.exp(-Math.min(1000,elapsedMillis) / 110.0);
        return Math.abs(next) < 0.00001 ? 0 : next;
    }
}
