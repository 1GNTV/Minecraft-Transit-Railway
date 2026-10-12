package dev.igntv.mtrengine.renderer;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.tool.Utilities;
import dev.igntv.mtrengine.MtrEngineForge;
import java.util.ArrayDeque;
/** Opt-in diagnostics: wall duration on render thread and delayed GPU timestamp intervals. */
public final class RenderPipelineMetrics {
    public enum Family { RAIL, TRAIN, DECORATION, OTHER }
    private static final boolean ENABLED = Boolean.getBoolean("mtr.v4.metrics") || Boolean.getBoolean("mtr.v4.gpuMetrics");
    private static final Stats[] stats = {new Stats(), new Stats(), new Stats(), new Stats()};
    private static final ArrayDeque<Pass> stack = new ArrayDeque<>();
    private static Family queuedFamily = Family.OTHER, drawingFamily = Family.OTHER;
    private static long lastReport = System.nanoTime();
    private static final class Stats {
        long passes, materials, wallNanos, gpuNanos, gpuSamples, matrixShaderPaths;
        final long[] queued = new long[4], draws = new long[4], triangles = new long[4];
        void clear() {
            passes = materials = wallNanos = gpuNanos = gpuSamples = matrixShaderPaths = 0;
            java.util.Arrays.fill(queued, 0); java.util.Arrays.fill(draws, 0); java.util.Arrays.fill(triangles, 0);
        }
    }
    private static final class Pass {
        final int category;
        final Family previousDrawing = drawingFamily;
        final long start = System.nanoTime();
        final GpuPassTimer.Sample gpu;
        Pass(int category) { this.category = category; gpu = GpuPassTimer.begin(category); }
    }
    private RenderPipelineMetrics() {}
    public static Family pushQueueFamily(Family family) {
        final Family previous = queuedFamily;
        queuedFamily = family;
        return previous;
    }
    public static void restoreQueueFamily(Family previous) { queuedFamily = previous; }
    public static Family captureFamily() { return queuedFamily; }
    private static int category(boolean shaderless) { return (shaderless ? 0 : 2) + (OptimizedRenderer.renderingShadows() ? 1 : 0); }
    public static void queued(Family family) {
        if (ENABLED) stats[category(Utilities.canUseCustomShader())].queued[family.ordinal()]++;
    }
    public static void drawing(Family family) { if (ENABLED) drawingFamily = family; }
    public static void begin(boolean shaderless) {
        if (ENABLED) {
            final Pass pass = new Pass(category(shaderless));
            stack.push(pass);
            stats[pass.category].passes++;
        }
    }
    public static void draw(int indexCount) {
        if (ENABLED && !stack.isEmpty()) {
            final Stats value = stats[stack.peek().category];
            value.draws[drawingFamily.ordinal()]++;
            value.triangles[drawingFamily.ordinal()] += indexCount / 3;
        }
    }
    public static void instancedDraw(int indexCount, int instances) {
        if (ENABLED && !stack.isEmpty()) {
            final Stats value = stats[stack.peek().category];
            value.draws[drawingFamily.ordinal()]++;
            value.triangles[drawingFamily.ordinal()] += (long) (indexCount / 3) * instances;
        }
    }
    public static void material() { if (ENABLED && !stack.isEmpty()) stats[stack.peek().category].materials++; }
    public static void matrixShaderPath() { if (ENABLED && !stack.isEmpty()) stats[stack.peek().category].matrixShaderPaths++; }
    static void gpu(int category, long nanos) { stats[category].gpuNanos += nanos; stats[category].gpuSamples++; }
    public static void end() {
        if (!ENABLED || stack.isEmpty()) return;
        final Pass pass = stack.pop();
        GpuPassTimer.end(pass.gpu);
        stats[pass.category].wallNanos += System.nanoTime() - pass.start;
        drawingFamily = pass.previousDrawing;
        if (!stack.isEmpty()) return;
        final long now = System.nanoTime();
        if (now - lastReport < 10_000_000_000L) return;
        lastReport = now;
        InstancedModelRenderer.report();
        for (int i = 0; i < stats.length; i++) {
            final Stats value = stats[i];
            if (value.passes != 0 || value.gpuSamples != 0) {
                MtrEngineForge.LOGGER.info("MTR Work pipeline: shaderless={}, shadows={}, passes={}, materials={}, matrix-shader-paths={}, wall-us/pass={}, gpu-samples={}, gpu-us/sample={}",
                    i < 2, i % 2 == 1, value.passes, value.materials, value.matrixShaderPaths,
                    value.passes == 0 ? 0 : value.wallNanos / 1000.0 / value.passes,
                    value.gpuSamples, value.gpuSamples == 0 ? 0 : value.gpuNanos / 1000.0 / value.gpuSamples);
                for (Family family : Family.values()) {
                    final int n = family.ordinal();
                    if (value.queued[n] != 0 || value.draws[n] != 0)
                        MtrEngineForge.LOGGER.info("MTR Work family: shaderless={}, shadows={}, family={}, queued-meshes={}, drawn-meshes={}, triangles={}",
                            i < 2, i % 2 == 1, family, value.queued[n], value.draws[n], value.triangles[n]);
                }
            }
            value.clear();
        }
    }
}
