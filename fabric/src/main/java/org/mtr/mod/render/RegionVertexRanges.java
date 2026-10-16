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
    /** Immutable range with optional local-space bounds for GPU command culling. */
    public record Span(int first,int count,float x0,float y0,float z0,float x1,float y1,float z1) {
        public Span(int first,int count) {
            this(first,count,Float.POSITIVE_INFINITY,0,0,0,0,0);
        }
        public boolean bounded() {
            return Float.isFinite(x0+y0+z0+x1+y1+z1)
                && x0<=x1 && y0<=y1 && z0<=z1;
        }
    }
    private static final boolean INDIRECT = SillonMegaEngine.GPU_DRIVEN && !"false".equalsIgnoreCase(System.getProperty("mtr.engine.indirect", "true"));
    // Oculus-compatible shader draws can use the same standard GL4.3
    // DrawElementsIndirectCommand as shaderless. Keep this as an EXPLICIT
    // experiment until shader-pack visuals are validated on real hardware.
    private static final boolean SHADER_INDIRECT=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.shaderIndirect","true"));
    // GPU buffer bindings/uploads do not amortize over tiny 2-3 span pages.
    private static final int MIN_INDIRECT_SPANS = Math.max(2, Integer.getInteger("mtr.engine.indirectMinSpans", 8));
    private static final boolean HELIX_COALESCE = !"false".equalsIgnoreCase(System.getProperty("mtr.helix.coalesceSpans","true"));
    // Opaque compiled pages are index-order independent. Sorting their visible
    // ranges before adjacency compaction makes the GPU execute fewer commands.
    // Translucent, no-depth and cutout-hack materials retain exact draw order.
    private static final boolean SORT_OPAQUE = SillonMegaEngine.GPU_DRIVEN
        && !"false".equalsIgnoreCase(System.getProperty("mtr.mega.opaqueSpanSort", "true"));
    // GL indirect drawing under shader shadow cascades is a separate opt-in.
    // The normal proven glMultiDrawElements fallback remains the default.
    private static final boolean HELIX_SHADOW_INDIRECT = Boolean.getBoolean("mtr.helix.shadowIndirect");
    private static List<Span> compact(List<Span> spans) {
        if (!HELIX_COALESCE || spans.size() < 2) return spans;
        List<Span> compacted = null;
        int cursor = 0;
        while (cursor < spans.size()) {
            Span start = spans.get(cursor);
            if (start.first() < 0 || start.count() < 0) throw new IllegalArgumentException("Invalid region span");
            int first = start.first(), count = start.count();
            Span merged=start;
            int next = cursor + 1;
            // Only merge adjacent and ordered index ranges; never change draw order.
            while (next < spans.size()) {
                Span part = spans.get(next);
                if (part.first() != (long) first + count || part.count() < 0 || (long) count + part.count() > Integer.MAX_VALUE) break;
                count += part.count();
                merged = merged.bounded() && part.bounded()
                    ? new Span(first,count,
                        Math.min(merged.x0(),part.x0()),Math.min(merged.y0(),part.y0()),Math.min(merged.z0(),part.z0()),
                        Math.max(merged.x1(),part.x1()),Math.max(merged.y1(),part.y1()),Math.max(merged.z1(),part.z1()))
                    : new Span(first,count);
                next++;
            }
            if (next > cursor + 1 && compacted == null) {
                compacted = new ArrayList<>(spans.size());
                compacted.addAll(spans.subList(0, cursor));
            }
            if (compacted != null) compacted.add(merged);
            cursor = next;
        }
        return compacted == null ? spans : compacted;
    }
    private static final Map<VertexArray, Ranges> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean indirectFailed;

    private static final class Ranges {
        IntBuffer counts;
        PointerBuffer offsets;
        int indices;
        List<Span> last = List.of();
        ByteBuffer commandBytes;
        int indirectBuffer, boundsBuffer;
        float originX,originY,originZ;
        boolean hasWorldOrigin;
        boolean indirectDirty = true, boundsDirty=true, gpuModified;

        Ranges(int capacity) {
            counts = BufferUtils.createIntBuffer(capacity);
            offsets = BufferUtils.createPointerBuffer(capacity);
        }
        void release() {
            if (indirectBuffer != 0) {
                GL43.glDeleteBuffers(indirectBuffer);
                indirectBuffer = 0;
            }
            if(boundsBuffer!=0) {
                GL43.glDeleteBuffers(boundsBuffer);
                boundsBuffer=0;
            }
            last = List.of();
            commandBytes = null;
        }
    }

    private RegionVertexRanges() {}

    public static void select(VertexArray array, List<Span> spans) {
        if (SORT_OPAQUE && spans.size() >= 4 && !array.materialProperties.translucent
                && !array.materialProperties.cutoutHack
                && array.materialProperties.writeDepthBuf) {
            var ordered = new ArrayList<>(spans);
            ordered.sort(Comparator.comparingInt(Span::first));
            spans = ordered;
        }
        spans = compact(spans);
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
        ranges.boundsDirty = true;
    }

    /**
     * Region meshes contain coordinates local to their 16-block sector.
     * Only the render-thread caller that owns the baked page may register
     * that world-space origin. Normal arrays without it never GPU-cull.
     */
    public static void worldOrigin(VertexArray array,float x,float y,float z) {
        Ranges ranges=ACTIVE.get(array);
        if(ranges!=null) {
            ranges.originX=x; ranges.originY=y; ranges.originZ=z;
            ranges.hasWorldOrigin=true;
        }
    }

    private static void uploadBounds(Ranges ranges) {
        if(!ranges.boundsDirty && ranges.boundsBuffer!=0)return;
        int previous=GL43.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            if(ranges.boundsBuffer==0)ranges.boundsBuffer=GL43.glGenBuffers();
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,ranges.boundsBuffer);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                GpuDrivenRailCommands.encode(ranges.last),GL43.GL_DYNAMIC_DRAW);
            ranges.boundsDirty=false;
        } finally { GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,previous); }
    }

    private static boolean eligibleForIndirect() {
        if (!INDIRECT || indirectFailed || GlStateTracker.isGl4ES()
                || (OptimizedRenderer.renderingShadows() && !HELIX_SHADOW_INDIRECT)
                || (!Utilities.canUseCustomShader() && !SHADER_INDIRECT)) return false;
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
        ranges.gpuModified = false;
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
                // A previous compute pass may have set instanceCount=0 on
                // some commands. If the next pass is Oculus/shadow/non-compute,
                // restore ALL instance counts before normal indirect drawing.
                boolean compute=ranges.hasWorldOrigin
                    && !array.materialProperties.translucent
                    && !array.materialProperties.cutoutHack
                    && array.materialProperties.writeDepthBuf
                    && GpuDrivenRailCommands.supported(ranges.last.size());
                if (ranges.indirectDirty || ranges.indirectBuffer == 0
                        || (ranges.gpuModified && !compute)) upload(ranges);
                else GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,ranges.indirectBuffer);
                if (compute) {
                    uploadBounds(ranges);
                    ready = GpuDrivenRailCommands.cull(ranges.indirectBuffer,ranges.boundsBuffer,
                        ranges.last.size(),ranges.originX,ranges.originY,ranges.originZ);
                    ranges.gpuModified=ready;
                    if(!ready)ranges.indirectDirty=true;
                } else ready=true;
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
