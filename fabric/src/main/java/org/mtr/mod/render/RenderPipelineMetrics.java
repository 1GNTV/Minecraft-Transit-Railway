package org.mtr.mod.render;

import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.Init;

/** Opt-in wall durations on the render thread and exact MTR draw counts. */
public final class RenderPipelineMetrics {

	private static final boolean ENABLED = Boolean.getBoolean("mtr.v4.metrics");
	private static final long[] passes = new long[4], draws = new long[4], materials = new long[4], nanos = new long[4];
	private static int category;
	private static long start, lastReport;

	private RenderPipelineMetrics() {}

	public static void begin(boolean shaderless) {
		if (ENABLED) {
			category = (shaderless ? 0 : 2) + (OptimizedRenderer.renderingShadows() ? 1 : 0);
			start = System.nanoTime();
			passes[category]++;
		}
	}

	public static void draw() { if (ENABLED) draws[category]++; }
	public static void material() { if (ENABLED) materials[category]++; }

	public static void end() {
		if (!ENABLED) return;
		nanos[category] += System.nanoTime() - start;
		final long now = System.currentTimeMillis();
		if (now - lastReport < 10000) return;
		lastReport = now;
		for (int i = 0; i < passes.length; i++) {
			if (passes[i] != 0) {
				Init.LOGGER.info("MTR V4 pipeline: shaderless={}, shadows={}, passes={}, draws/pass={}, materials/pass={}, render-thread-us/pass={}", i < 2, i % 2 == 1, passes[i], draws[i] / (double) passes[i], materials[i] / (double) passes[i], nanos[i] / 1000.0 / passes[i]);
			}
			passes[i] = draws[i] = materials[i] = nanos[i] = 0;
		}
	}
}
