package dev.igntv.mtrengine.renderer;

/** Separate addon's pipeline families. The production profiling port follows
 * after rendering hooks are validated on official MTR and Oculus. */
public final class RenderPipelineMetrics {
    public enum Family { RAIL, TRAIN, DECORATION, OTHER }
    private RenderPipelineMetrics() {}
}
