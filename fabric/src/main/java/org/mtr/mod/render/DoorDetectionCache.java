package org.mtr.mod.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Retains stationary door/platform detection across real render frames,
 * including Oculus shadow passes. The original scan must still be repeated
 * at a bounded cadence to observe world edits and PSD unlock changes.
 *
 * Identity keys: vehicle, doorway resource, and world. Value keys: car slot,
 * door slot and full absolute car pose. No position rounding is allowed here.
 * This class performs no Minecraft calls and can be tested in isolation.
 */
public final class DoorDetectionCache<T> {
    public static final int REFRESH_FRAMES = 20;
    private static final int MAX_VEHICLES = 256;
    private static final int MAX_CARS = 64;
    private static final int MAX_DOORS = 128;
    private static final long UNUSED_FRAMES = 120;

    private static final class Entry<T> {
        Object doorway;
        double x, y, z, yaw, pitch;
        long lastScan;
        T result;

        boolean matches(Object box, double px, double py, double pz, double heading, double tilt) {
            return doorway == box &&
                Double.doubleToLongBits(x) == Double.doubleToLongBits(px) &&
                Double.doubleToLongBits(y) == Double.doubleToLongBits(py) &&
                Double.doubleToLongBits(z) == Double.doubleToLongBits(pz) &&
                Double.doubleToLongBits(yaw) == Double.doubleToLongBits(heading) &&
                Double.doubleToLongBits(pitch) == Double.doubleToLongBits(tilt);
        }
    }
    private static final class VehicleEntry<T> {
        final ArrayList<ArrayList<Entry<T>>> cars = new ArrayList<>();
        long seen;
    }

    private final IdentityHashMap<Object, VehicleEntry<T>> vehicles = new IdentityHashMap<>();
    private Object world;
    private long frame;
    private long hits, misses, scans;

    public void beginFrame() {
        frame++;
        if (frame % 64 == 0) {
            vehicles.entrySet().removeIf(entry -> frame - entry.getValue().seen > UNUSED_FRAMES);
        }
    }

    private boolean validSlot(int car, int door) {
        return car >= 0 && car < MAX_CARS && door >= 0 && door < MAX_DOORS;
    }

    private void ensureWorld(Object currentWorld) {
        if (world != currentWorld) {
            vehicles.clear();
            world = currentWorld;
        }
    }

    public T lookup(Object currentWorld, Object vehicle, int car, int door, Object doorway,
                    double x, double y, double z, double yaw, double pitch) {
        ensureWorld(currentWorld);
        if (vehicle == null || !validSlot(car, door)) { misses++; return null; }
        VehicleEntry<T> owner = vehicles.get(vehicle);
        if (owner != null) {
            owner.seen = frame;
            if (car < owner.cars.size()) {
                ArrayList<Entry<T>> slots = owner.cars.get(car);
                if (door < slots.size()) {
                    Entry<T> cached = slots.get(door);
                    if (cached != null && cached.matches(doorway, x, y, z, yaw, pitch)
                            && frame - cached.lastScan < REFRESH_FRAMES) {
                        hits++;
                        return cached.result;
                    }
                }
            }
        }
        misses++;
        return null;
    }

    /** Only called after an actual world scan; never on cache hits. */
    public void remember(Object currentWorld, Object vehicle, int car, int door, Object doorway,
                         double x, double y, double z, double yaw, double pitch, T scanned) {
        ensureWorld(currentWorld);
        scans++;
        if (vehicle == null || scanned == null || !validSlot(car, door)) return;
        VehicleEntry<T> owner = vehicles.get(vehicle);
        if (owner == null) {
            if (vehicles.size() >= MAX_VEHICLES) vehicles.clear();
            owner = new VehicleEntry<>();
            vehicles.put(vehicle, owner);
        }
        owner.seen = frame;
        while (owner.cars.size() <= car) owner.cars.add(new ArrayList<>());
        ArrayList<Entry<T>> slots = owner.cars.get(car);
        while (slots.size() <= door) slots.add(null);
        Entry<T> entry = slots.get(door);
        if (entry == null) {
            entry = new Entry<>();
            slots.set(door, entry);
        }
        entry.doorway = doorway;
        entry.x = x; entry.y = y; entry.z = z;
        entry.yaw = yaw; entry.pitch = pitch;
        entry.lastScan = frame;
        entry.result = scanned;
    }

    public void clear() { vehicles.clear(); world = null; }
    public long hits() { return hits; }
    public long misses() { return misses; }
    public long scans() { return scans; }
    public int vehicleCount() { return vehicles.size(); }
}
