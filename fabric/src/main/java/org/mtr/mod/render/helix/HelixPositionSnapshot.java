package org.mtr.mod.render.helix;

import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

/**
 * Source-format-independent, immutable SoA-ish snapshot of vehicle bogies.
 * Each JCM/public-API invocation gets its own mutable Vector/list objects;
 * no cached Vector can be modified by an addon. Uses 6 doubles per bogie
 * rather than allocating a second tree of pair/vector objects at cache fill.
 */
public final class HelixPositionSnapshot {
    private final VehicleCar[] cars;
    private final double[][] coords;

    private HelixPositionSnapshot(VehicleCar[] cars, double[][] coords) {
        this.cars = cars;
        this.coords = coords;
    }
    public static HelixPositionSnapshot capture(
            ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
                ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> source) {
        VehicleCar[] identities = new VehicleCar[source.size()];
        double[][] points = new double[source.size()][];
        for (int i = 0; i < source.size(); i++) {
            var car = source.get(i);
            identities[i] = car.left();
            var bogies = car.right();
            double[] values = new double[bogies.size() * 6];
            for (int j = 0; j < bogies.size(); j++) {
                var pair = bogies.get(j);
                Vector a = pair.left(), b = pair.right();
                int at = j * 6;
                values[at] = a.x; values[at+1] = a.y; values[at+2] = a.z;
                values[at+3] = b.x; values[at+4] = b.y; values[at+5] = b.z;
            }
            points[i] = values;
        }
        return new HelixPositionSnapshot(identities, points);
    }
    public ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
            ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> copy() {
        var output = new ObjectArrayList<ObjectObjectImmutablePair<VehicleCar,
            ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>>(cars.length);
        for (int i = 0; i < cars.length; i++) {
            double[] values = coords[i];
            var bogies = new ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>(values.length / 6);
            for (int j = 0; j < values.length; j += 6) {
                bogies.add(new ObjectObjectImmutablePair<>(
                    new Vector(values[j], values[j+1], values[j+2]),
                    new Vector(values[j+3], values[j+4], values[j+5])));
            }
            output.add(new ObjectObjectImmutablePair<>(cars[i], bogies));
        }
        return output;
    }
}
