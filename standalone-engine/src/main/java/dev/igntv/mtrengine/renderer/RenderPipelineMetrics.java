package dev.igntv.mtrengine.renderer;

/** Standalone render families and lightweight diagnostic API.
 * Full GPU timestamp metrics remain optional on this external renderer.
 */
public final class RenderPipelineMetrics {
    public enum Family { RAIL, TRAIN, DECORATION, OTHER }
    private RenderPipelineMetrics() {}
    public static void begin(boolean shaderless) {}
    public static void end() {}
    public static void draw(int indices) {}
    public static void material() {}
    public static void matrixShaderPath() {}
}
