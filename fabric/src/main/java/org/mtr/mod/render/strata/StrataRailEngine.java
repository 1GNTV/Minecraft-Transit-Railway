package org.mtr.mod.render.strata;

import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.MinecraftClientHelper;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.render.TrainVisibility;

/**
 * STRATA engine boundary for the legacy rail renderer.
 *
 * The public RenderRails.render() and its real lambda callbacks intentionally
 * stay in the original class so third-party Mixins can see the legacy hooks.
 * We move hot-path index selection, culling policy and diagnostics here without
 * adding live local variables at those addon injection sites.
 *
 * Called exclusively on the render thread. No per-pass context allocation and
 * no new persistent Rail objects. The spatial index remains owned by
 * MinecraftClientData and is invalidated by its existing update lifecycle.
 */
public final class StrataRailEngine {

	private static final boolean USE_RAIL_SPATIAL_INDEX = !"false".equalsIgnoreCase(System.getProperty("mtr.v4.railIndex", "true"));
	private static final boolean LOG_RAIL_METRICS = Boolean.getBoolean("mtr.v4.metrics");
	private static long lastRailMetricsMillis;
	private static long scanStartNanos;
	private static int railCandidateCount;
	private static int visibleRailCount;
	private static int totalRailCount;
	private static MinecraftClientData currentData;
	private static final boolean PASS_CANDIDATES = !"false".equalsIgnoreCase(System.getProperty("mtr.helix.passCandidates", "true"));
	private static MinecraftClientData candidateData;
	private static long candidateRevision;
	private static int candidateDistance;
	private static Iterable<MinecraftClientData.RailWrapper> candidateSnapshot;
	private static long candidateHits, candidateBuilds;

	private StrataRailEngine() {}

	public static Iterable<MinecraftClientData.RailWrapper> candidates(final Vector3d cameraPosition) {
		currentData = MinecraftClientData.getInstance();
		if (LOG_RAIL_METRICS) {
			totalRailCount = currentData.railWrapperList.size();
			scanStartNanos = System.nanoTime();
			railCandidateCount = 0;
			visibleRailCount = 0;
		}
		if (!USE_RAIL_SPATIAL_INDEX) return currentData.railWrapperList.values();
		if (!PASS_CANDIDATES) return currentData.railSpatialIndex.query(cameraPosition.getXMapped(), cameraPosition.getZMapped(), MinecraftClientHelper.getRenderDistance() * 16 + 32);
		// RailSpatialIndex.query already retains its ordered candidate list
		// until the broadphase cell bounds change. The former exact-camera
		// cache made a fresh ArrayList copy on every tiny camera movement,
		// including every Oculus shadow pass, even when the index returned
		// the same retained iterable. Keep the original instance instead.
		final int distance = MinecraftClientHelper.getRenderDistance() * 16 + 32;
		final Iterable<MinecraftClientData.RailWrapper> candidates =
			currentData.railSpatialIndex.query(cameraPosition.getXMapped(), cameraPosition.getZMapped(), distance);
		if(candidateData == currentData && candidateRevision == currentData.networkRevision
				&& candidateDistance == distance && candidateSnapshot == candidates)
			candidateHits++;
		else candidateBuilds++;
		candidateData = currentData;
		candidateRevision = currentData.networkRevision;
		candidateDistance = distance;
		candidateSnapshot = candidates;
		return candidates;
	}

	public static boolean visible(final MinecraftClientData.RailWrapper railWrapper, final boolean shadowPass) {
		if (LOG_RAIL_METRICS) railCandidateCount++;
		if (!railWrapper.shouldRender) return false;
		final Rail rail = railWrapper.getRail();
		if (shadowPass && !TrainVisibility.railShadowVisible(
				rail.railMath.minX, rail.railMath.minY, rail.railMath.minZ,
				rail.railMath.maxX, rail.railMath.maxY, rail.railMath.maxZ)) return false;
		if (LOG_RAIL_METRICS) visibleRailCount++;
		return true;
	}

	public static void finishPass(final boolean shadowPass) {
		if (!LOG_RAIL_METRICS || shadowPass) return;
		final long now = System.currentTimeMillis();
		if (now - lastRailMetricsMillis >= 10000) {
			lastRailMetricsMillis = now;
			Init.LOGGER.info("STRATA rail prefilter: total={}, candidates={}, visible={}, selection-us={}, index={}",
					totalRailCount, railCandidateCount, visibleRailCount,
					(System.nanoTime() - scanStartNanos) / 1000, USE_RAIL_SPATIAL_INDEX);
			Init.LOGGER.info("HELIX shared rail candidates: cache-hits={}, index-queries={}, size={}",
					candidateHits, candidateBuilds, candidateSnapshot instanceof java.util.Collection<?> list ? list.size() : -1);
			candidateHits = candidateBuilds = 0;
			Init.LOGGER.info("STRATA rail visibility: hits={}, misses={}, oversize={}",
					currentData.railVisibilityCache.hits(),
					currentData.railVisibilityCache.misses(),
					currentData.railVisibilityCache.uncached());
		}
	}
}
