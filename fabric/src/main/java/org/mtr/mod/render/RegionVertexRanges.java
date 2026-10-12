package org.mtr.mod.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;

/**
 * Immutable per-page geometry, mutable visibility. Identical visible spans do
 * not trigger CPU index repacking or GPU indirect-command uploads.
 * OpenGL 4.3 indirect drawing is restricted to the shaderless MTR backend;
 * Oculus and unknown addon shaders always retain the proven multi-draw path.
 */
public final class RegionVertexRanges {
    public record Span(int first, int count) {}
    private static final boolean INDIRECT = !"false".equalsIgnoreCase(System.getProperty("mtr.engine.indirect", "true"));
    // GPU buffer bindings/uploads do not amortize over tiny 2-3 span pages.
    private static final int MIN_INDIRECT_SPANS = Math.max(2, Integer.getInteger("mtr.engine.indirectMinSpans", 8));
    private static final Map<VertexArray, Ranges> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean indirectFailed;

    private static final class Ranges {
        IntBuffer counts;
        PointerBuffer offsets;
        int indices;
        List<Span> last = List.of();
        ByteBuffer commandBytes;
        int indirectBuffer;
        boolean indirectDirty = true;

        Ranges(int capacity) {
            counts = BufferUtils.createIntBuffer(capacity);
            offsets = BufferUtils.createPointerBuffer(capacity);
        }
        void release() {
            if (indirectBuffer != 0) {
                GL43.glDeleteBuffers(indirectBuffer);
                indirectBuffer = 0;
            }
            last = List.of();
            commandBytes = null;
        }
    }

    private RegionVertexRanges() {}

    public static void select(VertexArray array, List<Span> spans) {
        Ranges ranges = ACTIVE.get(array);
        if (ranges == null || ranges.counts.capacity() < spans.size()) {
            if (ranges != null) ranges.release();
            ranges = new Ranges(Math.max(16, spans.size()));
            ACTIVE.put(array, ranges);
        }
        if (ranges.last.equals(spans)) return;
        ranges.last = List.copyOf(spans);
        ranges.counts.clear();
        ranges.offsets.clear();
        ranges.indices = 0;
        final int bytes = array.indexBuffer.indexType == GL33.GL_UNSIGNED_BYTE ? 1
                : array.indexBuffer.indexType == GL33.GL_UNSIGNED_SHORT ? 2 : 4;
        for (Span span : spans) {
            if (span.first < 0 || span.count < 0) throw new IllegalArgumentException("Invalid region draw span");
            ranges.counts.put(span.count);
            ranges.offsets.put((long) span.first * bytes);
            ranges.indices += span.count;
        }
        ranges.counts.flip();
        ranges.offsets.flip();
        ranges.indirectDirty = true;
    }

    private static boolean eligibleForIndirect() {
        if (!INDIRECT || indirectFailed || GlStateTracker.isGl4ES() || OptimizedRenderer.renderingShadows()
                || !Utilities.canUseCustomShader()) return false;
        return GL.getCapabilities().OpenGL43;
    }

    private static void upload(Ranges ranges) {
        int count = ranges.last.size();
        int bytes = count * 20; // DrawElementsIndirectCommand: 5 uint32 fields
        if (ranges.commandBytes == null || ranges.commandBytes.capacity() < bytes)
            ranges.commandBytes = ByteBuffer.allocateDirect(Math.max(20, bytes)).order(ByteOrder.nativeOrder());
        ByteBuffer commands = ranges.commandBytes;
        commands.clear();
        for (Span span : ranges.last) {
            commands.putInt(span.count); // count
            commands.putInt(1);          // instanceCount
            commands.putInt(span.first); // firstIndex, not byte offset
            commands.putInt(0);          // baseVertex
            commands.putInt(0);          // baseInstance
        }
        commands.flip();
        if (ranges.indirectBuffer == 0) ranges.indirectBuffer = GL43.glGenBuffers();
        GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, ranges.indirectBuffer);
        GL43.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, commands, GL43.GL_DYNAMIC_DRAW);
        ranges.indirectDirty = false;
    }

    public static void draw(VertexArray array, int mode, int count, int type, long offset) {
        Ranges ranges = ACTIVE.get(array);
        if (ranges == null) {
            GL33.glDrawElements(mode, count, type, offset);
            return;
        }
        if (!ranges.counts.hasRemaining()) return;
        if (ranges.last.size() == 1) {
            Span s = ranges.last.get(0);
            int bytes = type == GL33.GL_UNSIGNED_BYTE ? 1 : type == GL33.GL_UNSIGNED_SHORT ? 2 : 4;
            GL33.glDrawElements(mode, s.count, type, (long) s.first * bytes);
            return;
        }
        if (ranges.last.size() >= MIN_INDIRECT_SPANS && eligibleForIndirect()) {
            final int previous = GL43.glGetInteger(GL43.GL_DRAW_INDIRECT_BUFFER_BINDING);
            boolean ready = false;
            try {
                if (ranges.indirectDirty || ranges.indirectBuffer == 0) upload(ranges);
                else GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, ranges.indirectBuffer);
                ready = true;
            } catch (RuntimeException ex) {
                indirectFailed = true;
            } finally {
                if (!ready) GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, previous);
            }
            if (ready) {
                try {
                    GL43.glMultiDrawElementsIndirect(mode, type, 0L, ranges.last.size(), 0);
                    return;
                } finally {
                    GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, previous);
                }
            }
        }
        // Always keep the original path available for shader packs/addons.
        GL33.glMultiDrawElements(mode, ranges.counts, type, ranges.offsets);
    }

    public static boolean contains(VertexArray array) { return ACTIVE.containsKey(array); }
    public static int indexCount(VertexArray array) {
        Ranges ranges = ACTIVE.get(array);
        return ranges == null ? array.indexBuffer.getVertexCount() : ranges.indices;
    }
    public static void remove(VertexArray array) {
        Ranges ranges = ACTIVE.remove(array);
        if (ranges != null) ranges.release();
    }
}
