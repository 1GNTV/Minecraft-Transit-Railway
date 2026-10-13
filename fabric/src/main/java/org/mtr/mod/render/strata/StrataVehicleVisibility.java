package org.mtr.mod.render.strata;

/**
 * Per-render-thread STRATA bridge for visibility decisions captured by
 * third-party MTR Mixins.
 *
 * JCM targets the actual javac-generated RenderVehicles model lambda by its
 * exact captured-parameter descriptor. Capturing an extra boolean changes
 * that descriptor and crashes the game. Keep this immutable boolean in a
 * nestable, thread-owned context while retaining the real lambda and all
 * existing visual culling semantics.
 *
 * ThreadLocal<Boolean> uses the JVM's cached Boolean.TRUE/FALSE objects;
 * no per-model allocation or synchronization is needed.
 */
public final class StrataVehicleVisibility {

	private static final ThreadLocal<Boolean> VISUAL = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private StrataVehicleVisibility() {}

	/** Set visibility for the current vehicle-model iteration. */
	public static boolean push(boolean visible) {
		final boolean previous = VISUAL.get();
		VISUAL.set(visible);
		return previous;
	}

	/** Cheap current-thread lookup, performed once per model callback. */
	public static boolean visible() {
		return VISUAL.get();
	}

	/** Nested render passes and exceptions restore the enclosing value. */
	public static void pop(boolean previous) {
		VISUAL.set(previous);
	}
}
