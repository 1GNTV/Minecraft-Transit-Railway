package org.mtr.mod.render.pixel;

/** PANAM's CSS timing functions, evaluated without rebuilding a surface. */
public final class PanamAnimation {
    public static final long ZOOM_MS = 2000, DEPARTURE_MS = 1500, NOTICE_MS = 10000;
    private PanamAnimation() {}
    public static double bezier(double x, double x1, double y1, double x2, double y2) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double lo = 0, hi = 1, t = x;
        for (int i = 0; i < 20; i++) {
            if (curve(t, x1, x2) < x) lo = t; else hi = t;
            t = (lo + hi) * .5;
        }
        return curve(t, y1, y2);
    }
    private static double curve(double t, double a, double b) {
        double u = 1 - t;
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
    }
    public static float zoom(long elapsed) {
        return (float) (1.4 - .4 * bezier(elapsed / 2000.0, .21, .92, .49, .97));
    }
    public static float departure(long elapsed) {
        return (float) bezier(elapsed / 1500.0, .38, -.17, .15, .92);
    }
    public static float noticeIcon(long elapsed) {
        return (float) bezier(elapsed / 1600.0, .21, .07, .49, 1);
    }
    public static float noticeText(long elapsed) {
        return (float) bezier(elapsed / 1000.0, .21, .07, .49, 1);
    }
    private static float ease(double p) { return (float) bezier(p, .25, .1, .25, 1); }
    public static float colon(long ms) {
        double p = Math.floorMod(ms, 1400) / 1400.0;
        return p < .5 ? 1 - .5f * ease(p * 2) : .5f + .5f * ease((p - .5) * 2);
    }
    public static float number(long ms) {
        double p = Math.floorMod(ms, 1000) / 1000.0;
        if (p < .4) return 1;
        if (p < .5) return 1 - .5f * ease((p - .4) * 10);
        if (p < .9) return .5f;
        return .5f + .5f * ease((p - .9) * 10);
    }
    public static float dot(long ms, int index) {
        return Math.floorMod(ms - index * 300L, 1400) < 280 ? 1 : .5f;
    }
}
