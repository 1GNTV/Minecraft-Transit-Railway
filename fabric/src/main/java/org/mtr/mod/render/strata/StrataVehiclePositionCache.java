package org.mtr.mod.render.strata;

/**
 * Per-vehicle, two-state snapshot cache for original and smoothed positions.
 * Used by the original VehicleExtension ABI, including unchanged JCM calls.
 * This class contains no dependency on JCM or the rendering backend.
 */
public final class StrataVehiclePositionCache<T> {
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("mtr.strata.vehiclePositionCache", "true"));

    private static final class Slot<T> {
        final long revision, progressBits, networkRevision;
        final boolean reversed;
        final Object path, cars;
        final T snapshot;

        Slot(long revision, double progress, boolean reversed, Object path,
             Object cars, long networkRevision, T snapshot) {
            this.revision = revision;
            progressBits = Double.doubleToLongBits(progress);
            this.reversed = reversed;
            this.path = path;
            this.cars = cars;
            this.networkRevision = networkRevision;
            this.snapshot = snapshot;
        }

        boolean matches(long revision, double progress, boolean reversed,
                        Object path, Object cars, long networkRevision) {
            return this.revision == revision
                && progressBits == Double.doubleToLongBits(progress)
                && this.reversed == reversed
                && this.path == path && this.cars == cars
                && this.networkRevision == networkRevision;
        }
    }

    private Slot<T> recent, older;

    public static boolean enabled() { return ENABLED; }

    public T find(long revision, double progress, boolean reversed,
                  Object path, Object cars, long networkRevision) {
        if (!ENABLED) return null;
        if (recent != null && recent.matches(revision, progress, reversed, path, cars, networkRevision))
            return recent.snapshot;
        if (older != null && older.matches(revision, progress, reversed, path, cars, networkRevision)) {
            Slot<T> oldRecent = recent;
            recent = older;
            older = oldRecent;
            return recent.snapshot;
        }
        return null;
    }

    public void store(long revision, double progress, boolean reversed,
                      Object path, Object cars, long networkRevision, T privateSnapshot) {
        if (!ENABLED) return;
        older = recent;
        recent = new Slot<>(revision, progress, reversed, path, cars, networkRevision,
                java.util.Objects.requireNonNull(privateSnapshot));
    }

    public void clear() { recent = older = null; }
}
