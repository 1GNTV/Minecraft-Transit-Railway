package org.mtr.mod.resource;

import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.data.IGui;
import org.mtr.mod.render.RenderPipelineMetrics;

import javax.annotation.Nullable;

public final class OptimizedRendererWrapper implements IGui {

	@Nullable
	private final OptimizedRenderer optimizedRenderer;

	public OptimizedRendererWrapper() {
		this.optimizedRenderer = OptimizedRenderer.hasOptimizedRendering() ? new OptimizedRenderer() : null;
	}

	public void beginReload() {
		if (optimizedRenderer != null) {
			optimizedRenderer.beginReload();
		}
	}

	public void finishReload() {
		if (optimizedRenderer != null) {
			optimizedRenderer.finishReload();
		}
	}

	public void queue(OptimizedModelWrapper optimizedModel, GraphicsHolder graphicsHolder, int light) {
		queue(optimizedModel, graphicsHolder, light, RenderPipelineMetrics.Family.OTHER);
	}

	public void queue(OptimizedModelWrapper optimizedModel, GraphicsHolder graphicsHolder, int light, RenderPipelineMetrics.Family family) {
		if (optimizedRenderer != null && optimizedModel.optimizedModel != null) {
			final RenderPipelineMetrics.Family previous = RenderPipelineMetrics.pushQueueFamily(family);
			try {
				optimizedRenderer.queue(optimizedModel.optimizedModel, graphicsHolder, ARGB_WHITE, light);
			} finally {
				RenderPipelineMetrics.restoreQueueFamily(previous);
			}
		}
	}

	public void render(boolean renderTranslucent) {
		if (optimizedRenderer != null) {
			optimizedRenderer.render(renderTranslucent);
		}
	}
}
