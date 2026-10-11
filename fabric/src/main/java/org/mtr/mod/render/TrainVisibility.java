package org.mtr.mod.render;

import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.Frustum;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.data.VehicleExtension;

/** Actual Minecraft pass frustum. Missing/stale captures fail open; nearby cars keep sound/boarding. */
public final class TrainVisibility {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.visibility", "true"));
    private static Frustum frustum;
    private static boolean shadows;
    private TrainVisibility() {}
    public static void beginFrame() { frustum = null; }
    public static void capture(Frustum current) { frustum = current; shadows = OptimizedRenderer.renderingShadows(); }
    private static boolean usable() { return ENABLED && frustum != null && shadows == OptimizedRenderer.renderingShadows(); }
    private static double radius(VehicleExtension vehicle, int index) {
        if (index >= vehicle.persistentVehicleData.longestDimensions.length) return Double.NaN;
        // Include pitched cars, gangways and overhang. Invalid metadata disables this rejection.
        return Math.max(16, vehicle.persistentVehicleData.longestDimensions[index] * 2 + 8);
    }
    public static boolean carVisible(VehicleExtension vehicle, int index, PositionAndRotation pose, Vector3d camera) {
        if (!usable()) return true;
        double r = radius(vehicle, index), x = pose.position.x, y = pose.position.y, z = pose.position.z;
        if (!Double.isFinite(r) || !Double.isFinite(x + y + z) || r <= 0) return true;
        // Nearby interaction and spatial audio must continue outside the camera view.
        if (Math.abs(x-camera.getXMapped()) <= r+48 && Math.abs(y-camera.getYMapped()) <= r+48 && Math.abs(z-camera.getZMapped()) <= r+48) return true;
        return frustum.isVisible(new Box(x-r, y-r, z-r, x+r, y+r, z+r));
    }
    public static boolean trainVisible(VehicleExtension vehicle, ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectObjectImmutablePair<ObjectArrayList<PositionAndRotation>, PositionAndRotation>>> cars, Vector3d camera) {
        if (!usable() || cars.isEmpty()) return true;
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
        for (int i=0;i<cars.size();i++) {
            PositionAndRotation p=cars.get(i).right().right(); double r=radius(vehicle,i);
            if (!Double.isFinite(r) || !Double.isFinite(p.position.x+p.position.y+p.position.z)) return true;
            x0=Math.min(x0,p.position.x-r);y0=Math.min(y0,p.position.y-r);z0=Math.min(z0,p.position.z-r);
            x1=Math.max(x1,p.position.x+r);y1=Math.max(y1,p.position.y+r);z1=Math.max(z1,p.position.z+r);
        }
        if (camera.getXMapped()>=x0-48 && camera.getXMapped()<=x1+48 && camera.getYMapped()>=y0-48 && camera.getYMapped()<=y1+48 && camera.getZMapped()>=z0-48 && camera.getZMapped()<=z1+48) return true;
        return frustum.isVisible(new Box(x0,y0,z0,x1,y1,z1));
    }
}
