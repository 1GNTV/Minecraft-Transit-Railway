package org.mtr.mod.render;

/** Render-thread-safe conservative distance guard for shader shadow casters.
 * A zero radius means unbounded. Non-finite or inverted bounds always fail
 * open to avoid losing unusual addon meshes.
 */
public final class ShadowDistanceBudget {
    private ShadowDistanceBudget() {}

    /** True when a world-space rail AABB intersects the given XZ radius. */
    public static boolean visible(double cameraX,double cameraZ,
                                  double minX,double maxX,double minZ,double maxZ,
                                  double radius) {
        if(radius<=0 || !Double.isFinite(radius))return true;
        if(!Double.isFinite(cameraX+cameraZ+minX+maxX+minZ+maxZ)
                || minX>maxX || minZ>maxZ)return true;
        double dx=Math.max(Math.max(minX-cameraX,cameraX-maxX),0);
        double dz=Math.max(Math.max(minZ-cameraZ,cameraZ-maxZ),0);
        return dx*dx+dz*dz<=radius*radius;
    }

    /** Clamp a larger legacy train-shadow range to an optional close budget. */
    public static double trainRadius(double oldDistance,int budget) {
        return budget>0 ? Math.min(oldDistance,budget) : oldDistance;
    }
}
