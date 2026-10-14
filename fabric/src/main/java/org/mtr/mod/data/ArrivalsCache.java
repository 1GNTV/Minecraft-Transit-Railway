package org.mtr.mod.data;

import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongCollection;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.function.Consumer;

public abstract class ArrivalsCache {

	private long nextRequest;
	private long revision, receivedAt, generation;
	public final long revision() { return revision; }
	public final long receivedAt() { return receivedAt; }
	public final void invalidate() { generation++; revision++; receivedAt = 0; nextRequest = 0; queuedPlatformIdsWithAge.clear(); arrivalResponseCache.clear(); }
	private final Long2IntAVLTreeMap queuedPlatformIdsWithAge = new Long2IntAVLTreeMap();
	private final ObjectArrayList<ArrivalResponse> arrivalResponseCache = new ObjectArrayList<>();
	private final int cachedMillis;
	private static final int PERSISTENT_AGE = 5;

	protected ArrivalsCache(int cachedMillis) {
		this.cachedMillis = cachedMillis;
	}

	public final ObjectArrayList<ArrivalResponse> requestArrivals(LongCollection platformIds) {
		if (queuedPlatformIdsWithAge.isEmpty() && canSendRequest()) {
			nextRequest = System.currentTimeMillis() + 100;
		}

		platformIds.forEach(platformId -> queuedPlatformIdsWithAge.put(platformId, 0));

		final ObjectArrayList<ArrivalResponse> arrivals = new ObjectArrayList<>();
		arrivalResponseCache.forEach(arrivalResponse -> {
			if (platformIds.contains(arrivalResponse.getPlatformId())) {
				arrivals.add(arrivalResponse);
			}
		});

		return arrivals;
	}

	public final void tick() {
		if (!queuedPlatformIdsWithAge.isEmpty() && canSendRequest()) {
			final LongAVLTreeSet platformIds = new LongAVLTreeSet(queuedPlatformIdsWithAge.keySet());

			final long requestGeneration = generation;
			requestArrivalsFromServer(platformIds, arrivalResponseList -> {
				if (requestGeneration != generation) return;
				arrivalResponseCache.clear();
				arrivalResponseCache.addAll(arrivalResponseList);
				revision++;
				receivedAt = System.currentTimeMillis();
			});

			platformIds.forEach(platformId -> queuedPlatformIdsWithAge.compute(platformId, (key, age) -> age > PERSISTENT_AGE ? null : age + 1));
			nextRequest = System.currentTimeMillis() + cachedMillis;
		}
	}

	private boolean canSendRequest() {
		return System.currentTimeMillis() >= nextRequest;
	}

	public abstract long getMillisOffset();

	protected abstract void requestArrivalsFromServer(LongAVLTreeSet platformIds, Consumer<ObjectList<ArrivalResponse>> callback);
}
