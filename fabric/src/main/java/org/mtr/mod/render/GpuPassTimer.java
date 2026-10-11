package org.mtr.mod.render;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import java.util.ArrayDeque;
/** Bounded timestamp queries; never wait for a result or interfere with active query targets. */
public final class GpuPassTimer {
    private static final int MAX_PENDING = 32;
    private static final boolean REQUESTED = Boolean.getBoolean("mtr.v4.gpuMetrics");
    private static final ArrayDeque<Sample> pending = new ArrayDeque<>(), available = new ArrayDeque<>();
    private static boolean checked, supported;
    private static int allocated;
    public static final class Sample {
        private final int start = GL33.glGenQueries(), end = GL33.glGenQueries();
        private int category;
    }
    private GpuPassTimer() {}
    public static Sample begin(int category) {
        if (!REQUESTED) return null;
        if (!checked) {
            supported = GL.getCapabilities().OpenGL33;
            checked = true;
            if (!supported) org.mtr.mod.Init.LOGGER.info("MTR GPU timer unavailable: OpenGL 3.3 timestamps required");
        }
        if (!supported) return null;
        poll();
        final Sample sample;
        if (!available.isEmpty()) sample = available.removeFirst();
        else if (allocated < MAX_PENDING) { sample = new Sample(); allocated++; }
        else return null;
        sample.category = category;
        GL33.glQueryCounter(sample.start, GL33.GL_TIMESTAMP);
        return sample;
    }
    public static void end(Sample sample) {
        if (sample == null) return;
        GL33.glQueryCounter(sample.end, GL33.GL_TIMESTAMP);
        pending.addLast(sample);
    }
    private static void poll() {
        for (int n = 0; n < 4 && !pending.isEmpty(); n++) {
            final Sample sample = pending.peekFirst();
            if (GL33.glGetQueryObjecti(sample.end, GL33.GL_QUERY_RESULT_AVAILABLE) == 0 ||
                    GL33.glGetQueryObjecti(sample.start, GL33.GL_QUERY_RESULT_AVAILABLE) == 0) return;
            final long start = GL33.glGetQueryObjectui64(sample.start, GL33.GL_QUERY_RESULT);
            final long end = GL33.glGetQueryObjectui64(sample.end, GL33.GL_QUERY_RESULT);
            pending.removeFirst();
            if (end >= start) RenderPipelineMetrics.gpu(sample.category, end - start);
            available.addLast(sample);
        }
    }
}

