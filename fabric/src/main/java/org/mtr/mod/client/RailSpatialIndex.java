package org.mtr.mod.client;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Comparator;

/**
 * Conservative X/Z prefilter for MTR rails. The actual occlusion and per-segment
 * distance/angle checks still run in RenderRails. No geometry is omitted based
 * on camera direction, height, or opaque blocks at this stage.
 *
 * Rebuilt only when the static network is synchronized. Candidate lists are
 * retained until the camera's query-cell bounds change.
 */
public final class RailSpatialIndex {

	private static final int CELL_SIZE = 128;
	private static final int MAX_CELLS_PER_RAIL = 256;
	private static final int MAX_QUERY_CELLS = 1024;
	private final Long2ObjectOpenHashMap<ObjectArrayList<Entry>> cells = new Long2ObjectOpenHashMap<>();
	private final ObjectArrayList<Entry> oversized = new ObjectArrayList<>();
	private final ObjectArrayList<MinecraftClientData.RailWrapper> all = new ObjectArrayList<>();
	private final ObjectArrayList<MinecraftClientData.RailWrapper> empty = new ObjectArrayList<>();
	private ObjectArrayList<MinecraftClientData.RailWrapper> lastCandidates = empty;
	private long lastX1 = Long.MIN_VALUE, lastX2 = Long.MIN_VALUE;
	private long lastZ1 = Long.MIN_VALUE, lastZ2 = Long.MIN_VALUE;

	public void rebuild(Iterable<MinecraftClientData.RailWrapper> railWrappers) {
		cells.clear();
		oversized.clear();
		all.clear();
		int order = 0;
		for (final MinecraftClientData.RailWrapper rail : railWrappers) {
			all.add(rail);
			final Entry entry = new Entry(rail, order++);
			final double minX = rail.startVector.x;
			final double minZ = rail.startVector.z;
			final double maxX = rail.endVector.x;
			final double maxZ = rail.endVector.z;
			if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
					|| minX > maxX || minZ > maxZ) {
				oversized.add(entry); // Fail open on invalid bounds.
				continue;
			}
			final long x1 = cell(minX);
			final long z1 = cell(minZ);
			final long x2 = cell(maxX);
			final long z2 = cell(maxZ);
			if (tooLarge(x1, z1, x2, z2, MAX_CELLS_PER_RAIL)) {
				oversized.add(entry);
				continue;
			}
			for (long x = x1; x <= x2; x++) {
				for (long z = z1; z <= z2; z++) {
					cells.computeIfAbsent(key(x, z), ignored -> new ObjectArrayList<>()).add(entry);
				}
			}
		}
		invalidateQuery();
	}

	/**
	 * Returns all rails whose *broadphase buckets* overlap the camera's horizontal
	 * visibility square, with a conservative external margin supplied by caller.
	 * The returned list preserves the original rail iteration order.
	 */
	public Iterable<MinecraftClientData.RailWrapper> query(double cameraX, double cameraZ, int radius) {
		if (!Double.isFinite(cameraX) || !Double.isFinite(cameraZ) || radius < 0) {
			return all; // Fail open rather than incorrectly cull.
		}
		final long x1 = cell(cameraX - radius);
		final long z1 = cell(cameraZ - radius);
		final long x2 = cell(cameraX + radius);
		final long z2 = cell(cameraZ + radius);
		if (tooLarge(x1, z1, x2, z2, MAX_QUERY_CELLS)) {
			return all;
		}
		if (x1 == lastX1 && x2 == lastX2 && z1 == lastZ1 && z2 == lastZ2) {
			return lastCandidates;
		}
		final ObjectOpenHashSet<Entry> matches = new ObjectOpenHashSet<>(oversized);
		for (long x = x1; x <= x2; x++) {
			for (long z = z1; z <= z2; z++) {
				final ObjectArrayList<Entry> bucket = cells.get(key(x, z));
				if (bucket != null) {
					matches.addAll(bucket);
				}
			}
		}
		final ObjectArrayList<Entry> sorted = new ObjectArrayList<>(matches);
		sorted.sort(Comparator.comparingInt(entry -> entry.order));
		final ObjectArrayList<MinecraftClientData.RailWrapper> candidates = new ObjectArrayList<>(sorted.size());
		sorted.forEach(entry -> candidates.add(entry.rail));
		lastCandidates = candidates;
		lastX1 = x1;
		lastX2 = x2;
		lastZ1 = z1;
		lastZ2 = z2;
		return lastCandidates;
	}

	private void invalidateQuery() {
		lastX1 = lastX2 = lastZ1 = lastZ2 = Long.MIN_VALUE;
		lastCandidates = empty;
	}

	private static long cell(double coordinate) {
		return (long) Math.floor(coordinate / CELL_SIZE);
	}

	private static boolean tooLarge(long x1, long z1, long x2, long z2, int limit) {
		return x2 < x1 || z2 < z1 ||
				x1 < Integer.MIN_VALUE || x2 > Integer.MAX_VALUE ||
				z1 < Integer.MIN_VALUE || z2 > Integer.MAX_VALUE ||
				x2 - x1 >= limit || z2 - z1 >= limit ||
				(x2 - x1 + 1) * (z2 - z1 + 1) > limit;
	}

	private static long key(long x, long z) {
		return (x << 32) | (z & 0xFFFFFFFFL);
	}

	private static final class Entry {
		private final MinecraftClientData.RailWrapper rail;
		private final int order;

		private Entry(MinecraftClientData.RailWrapper rail, int order) {
			this.rail = rail;
			this.order = order;
		}
	}
}
