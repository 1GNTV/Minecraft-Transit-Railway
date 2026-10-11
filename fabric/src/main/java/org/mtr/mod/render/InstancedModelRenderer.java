package org.mtr.mod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import org.mtr.mapping.holder.Matrix4f;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.mtr.mapping.render.vertex.VertexAttributeType;
import org.mtr.mod.Init;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Shaderless GL 3.3 instances. Original VAOs and shader setup remain untouched. */
public final class InstancedModelRenderer {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("mtr.next.instances", "true"));
    private static final int MAX_INSTANCES = 1024, MAX_SLOTS = 64, PAGES = 3, MATRIX_BYTES = 64;
    private static final Map<VertexArray, Slot> SLOTS = new LinkedHashMap<>(64, 0.75F, true);
    private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(MAX_INSTANCES * MATRIX_BYTES).order(ByteOrder.nativeOrder());
    private static final FloatBuffer FLOATS = SCRATCH.asFloatBuffer();
    private static final FloatBuffer[] MATRIX_VIEWS = new FloatBuffer[MAX_INSTANCES];
    static { for (int i = 0; i < MAX_INSTANCES; i++) MATRIX_VIEWS[i] = FLOATS.slice(i << 4, 16); }
    private static final float[] VALUES = new float[MAX_INSTANCES * 16];
    private static final VertexAttributeType[] TYPES = VertexAttributeType.values();
    private static boolean failed;
    private static long submittedGroups, submittedInstances, uploads, reused, busy;

    private static final class Page {
        int vao, buffer, capacity, count;
        long fence;
        float[] values;
        boolean available() {
            if (fence == 0) return true;
            int status = GL33.glClientWaitSync(fence, 0, 0L);
            if (status != GL33.GL_ALREADY_SIGNALED && status != GL33.GL_CONDITION_SATISFIED) return false;
            GL33.glDeleteSync(fence); fence = 0;
            return true;
        }
        boolean same(int count) {
            if (this.count != count || values == null) return false;
            for (int i = 0; i < count * 16; i++) if (Float.floatToRawIntBits(values[i]) != Float.floatToRawIntBits(VALUES[i])) return false;
            return true;
        }
        void close() {
            if (fence != 0) GL33.glDeleteSync(fence);
            if (vao != 0) GL33.glDeleteVertexArrays(vao);
            if (buffer != 0) GL33.glDeleteBuffers(buffer);
            fence = 0; vao = buffer = 0; values = null;
        }
    }
    private static final class Slot {
        final VertexArray source;
        final Page[] pages = new Page[PAGES];
        Slot(VertexArray source) { this.source = source; }
        void close() { for (Page page : pages) if (page != null) page.close(); }
    }
    private InstancedModelRenderer() {}

    public static void draw(List<?> calls, Consumer<Object> ordinary) {
        if (!ENABLED || failed || (Utilities.canUseCustomShader() && OptimizedRenderer.renderingShadows()) || GlStateTracker.isGl4ES()) {
            calls.forEach(ordinary); return;
        }
        if (!GL.getCapabilities().OpenGL33) { calls.forEach(ordinary); return; }
        ConsecutiveRuns.visit(calls, Utilities.canUseCustomShader() ? MAX_INSTANCES : InstanceShaderSource.CAPACITY, InstancedModelRenderer::compatible, (start, end) -> {
            boolean drawn = false;
            if (end - start >= 4 && calls.get(start) instanceof RenderCallData) {
                try { drawn = Utilities.canUseCustomShader() ? drawRun(calls, start, end) : OculusInstanceRenderer.draw(calls, start, end); }
                catch (RuntimeException error) {
                    failed = true;
                    Init.LOGGER.error("MTR Next instances disabled for this resource generation; ordinary draws restored", error);
                } finally { GlVertexAttributeCache.invalidate(); }
            }
            if (!drawn) for (int i = start; i < end; i++) ordinary.accept(calls.get(i));
        });
    }

    private static boolean eligible(RenderCallData call) {
        VertexArray array = call.mtr$array();
        VertexAttributeState state = call.mtr$attributes();
        if (RegionVertexRanges.contains(array) || !ModelPartVisibility.visible(array,state)) return false;
        if (!((Object) array instanceof VertexArrayIdentity) || ((VertexArrayIdentity) (Object) array).mtr$vaoId() == 0 || state.matrix4f == null) return false;
        if (array.materialProperties.translucent || array.materialProperties.cutoutHack || !array.materialProperties.writeDepthBuf) return false;
        if (state.position != null || state.normal != null || state.textureU != null || state.textureV != null) return false;
        for (VertexAttributeType type : TYPES) {
            VertexAttributeSource source = array.mapping.sources.get(type);
            if (type == VertexAttributeType.MATRIX_MODEL && source != VertexAttributeSource.GLOBAL) return false;
            if (source != VertexAttributeSource.GLOBAL && source != VertexAttributeSource.VERTEX_BUFFER) return false;
        }
        return array.materialProperties.vertexAttributeState.matrix4f == null;
    }
    private static boolean compatible(Object left, Object right) {
        if (!(left instanceof RenderCallData) || !(right instanceof RenderCallData)) return false;
        RenderCallData a = (RenderCallData) left, b = (RenderCallData) right;
        if (a.mtr$array() != b.mtr$array() || a.mtr$family() != b.mtr$family() || !eligible(a) || !eligible(b)) return false;
        VertexAttributeState x = a.mtr$attributes(), y = b.mtr$attributes();
        return Objects.equals(x.color, y.color) && Objects.equals(x.lightmapUV, y.lightmapUV) && Objects.equals(x.overlayUV, y.overlayUV);
    }

    private static boolean drawRun(List<?> calls, int start, int end) {
        RenderCallData first = (RenderCallData) calls.get(start);
        if (!eligible(first)) return false;
        VertexArray source = first.mtr$array();
        int count = end - start;
        FLOATS.clear();
        for (int i = start; i < end; i++) {
            // Utilities.store uses absolute writes; use a 16-float slice for each matrix.
            FloatBuffer matrix = MATRIX_VIEWS[i - start];
            matrix.clear();
            Matrix4f value = ((RenderCallData) calls.get(i)).mtr$attributes().matrix4f;
            Utilities.store(value, matrix);
        }
        FLOATS.get(0, VALUES, 0, count * 16);
        Slot slot = SLOTS.get(source);
        if (slot == null) {
            if (SLOTS.size() == MAX_SLOTS) {
                Iterator<Slot> iterator = SLOTS.values().iterator(); Slot victim = iterator.next(); iterator.remove(); victim.close();
            }
            slot = new Slot(source); SLOTS.put(source, slot);
        }
        Page page = null;
        for (Page candidate : slot.pages) if (candidate != null && candidate.same(count)) { page = candidate; reused++; break; }
        if (page == null) {
            for (int i = 0; i < PAGES; i++) {
                if (slot.pages[i] == null) slot.pages[i] = createPage(source);
                if (slot.pages[i].available()) { page = slot.pages[i]; break; }
            }
            if (page == null) { busy++; return false; }
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, page.buffer);
            int bytes = count * MATRIX_BYTES;
            if (page.capacity < bytes) {
                page.capacity = Integer.highestOneBit(bytes - 1) << 1;
                GL33.glBufferData(GL33.GL_ARRAY_BUFFER, page.capacity, GL33.GL_STREAM_DRAW);
            }
            SCRATCH.position(0).limit(bytes);
            GL33.glBufferSubData(GL33.GL_ARRAY_BUFFER, 0, SCRATCH);
            if (page.values == null || page.values.length < count * 16) page.values = new float[page.capacity / Float.BYTES];
            System.arraycopy(VALUES, 0, page.values, 0, count * 16); page.count = count; uploads++;
        }
        GL33.glBindVertexArray(page.vao);
        first.mtr$attributes().apply();
        source.materialProperties.vertexAttributeState.apply();
        RenderPipelineMetrics.drawing(first.mtr$family());
        GL33.glDrawElementsInstanced(GL33.GL_TRIANGLES, source.indexBuffer.getVertexCount(), source.indexBuffer.indexType, 0L, count);
        RenderPipelineMetrics.instancedDraw(source.indexBuffer.getVertexCount(), count);
        if (page.fence != 0) GL33.glDeleteSync(page.fence);
        page.fence = GL33.glFenceSync(GL33.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        submittedGroups++; submittedInstances += count;
        return true;
    }

    private static Page createPage(VertexArray source) {
        Page page = new Page();
        source.bind();
        int indexBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int[] buffers = new int[TYPES.length];
        for (VertexAttributeType type : TYPES) {
            if (source.mapping.sources.get(type) == VertexAttributeSource.VERTEX_BUFFER) {
                buffers[type.ordinal()] = GL33.glGetVertexAttribi(type.location, GL33.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
                if (buffers[type.ordinal()] == 0) throw new IllegalArgumentException("Missing vertex buffer for " + type);
            }
        }
        if (indexBuffer == 0) throw new IllegalArgumentException("Missing source index buffer");
        try {
            page.vao = GL33.glGenVertexArrays(); page.buffer = GL33.glGenBuffers();
            GL33.glBindVertexArray(page.vao);
            GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            for (VertexAttributeType type : TYPES) {
                if (source.mapping.sources.get(type) == VertexAttributeSource.VERTEX_BUFFER) {
                    GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, buffers[type.ordinal()]);
                    type.toggleAttributeArray(true);
                    type.setupAttributePointer(source.mapping.strideVertex, source.mapping.pointers.get(type));
                    for (int n = 0; n < type.span; n++) GL33.glVertexAttribDivisor(type.location + n, 0);
                }
            }
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, page.buffer);
            VertexAttributeType.MATRIX_MODEL.toggleAttributeArray(true);
            VertexAttributeType.MATRIX_MODEL.setupAttributePointer(MATRIX_BYTES, 0);
            for (int n = 0; n < 4; n++) GL33.glVertexAttribDivisor(VertexAttributeType.MATRIX_MODEL.location + n, 1);
            return page;
        } catch (RuntimeException | Error error) { page.close(); throw error; }
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(InstancedModelRenderer::clear); return; }
        SLOTS.values().forEach(Slot::close); SLOTS.clear(); failed = false; OculusInstanceRenderer.clear();
        submittedGroups = submittedInstances = uploads = reused = busy = 0;
    }
    public static void report() {
        if (submittedGroups != 0 || busy != 0) Init.LOGGER.info("MTR Next instances: groups={}, instances={}, uploads={}, reused={}, busy-fallbacks={}, resident-slots={}", submittedGroups, submittedInstances, uploads, reused, busy, SLOTS.size());
        submittedGroups = submittedInstances = uploads = reused = busy = 0;
    }
}
