package org.mtr.mod.resource;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class CachedResource<T> {

	@Nullable
	private T data;
	private long expiry;

	private final Supplier<T> dataSupplier;
	private final long lifespan;

	private static boolean canFetchCache;
	private static final WeakExpiryQueue<CachedResource<?>> CACHED_RESOURCES = new WeakExpiryQueue<>();
	private WeakExpiryQueue.Ticket<CachedResource<?>> expiryTicket;

	public CachedResource(final Supplier<T> dataSupplier, final long lifespan) {
		this.dataSupplier = dataSupplier;
		this.lifespan = lifespan;
	}

	@Nullable
	public T getData(boolean force) {
		synchronized (CACHED_RESOURCES) {
		if (force || canFetchCache) {
			final long currentMillis = System.currentTimeMillis();
			if (data == null || currentMillis > expiry) {
				data = dataSupplier.get();
				canFetchCache = false;
			}
			expiry = currentMillis + lifespan;
			if (data != null && expiryTicket == null) expiryTicket = CACHED_RESOURCES.add(this, expiry);
			else if (data == null && expiryTicket != null) {
				CACHED_RESOURCES.remove(expiryTicket);
				expiryTicket = null;
			}
		}
		return data;
		}
	}

	public boolean hasData() { synchronized (CACHED_RESOURCES) { return data != null && System.currentTimeMillis() <= expiry; } }

	public static void tick() {
		synchronized (CACHED_RESOURCES) {
		canFetchCache = true;
		final long currentMillis = System.currentTimeMillis();
		CACHED_RESOURCES.expire(currentMillis, cachedResource -> {
			if (currentMillis > cachedResource.expiry) {
				cachedResource.data = null;
				cachedResource.expiryTicket = null;
				return WeakExpiryQueue.DONE;
			}
			// Access extended the TTL; reschedule once when the old deadline becomes due.
			return cachedResource.expiry;
		});
		}
	}
}
