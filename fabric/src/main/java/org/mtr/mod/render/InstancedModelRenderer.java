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
    // Experimental Oculus shadow instancing; unpatched programs fall back.
    private static final boolean HELIX_SHADOW_INSTANCES = Boolean.getBoolean("mtr.helix.shadowInstances");
    private static final int MAX_INSTANCES = 1024, MAX_SLOTS = 64, PAGES = 3, MATRIX_BYTES = 76, WORDS = 19;
    private static final Map<VertexArray, Slot> SLOTS = new LinkedHashMap<>(64, 0.75F, true);
    private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(MAX_INSTANCES * MATRIX_BYTES).order(ByteOrder.nativeOrder());
    private static final FloatBuffer FLOATS = SCRATCH.asFloatBuffer();
    private static final FloatBuffer[] MATRIX_VIEWS = new FloatBuffer[MAX_INSTANCES];
    static { for (int i = 0; i < MAX_INSTANCES; i++) MATRIX_VIEWS[i] = FLOATS.slice(i * WORDS, 16); }
    private static final int[] VALUES = new int[MAX_INSTANCES * WORDS];
    private static final VertexAttributeType[] TYPES = VertexAttributeType.values();
    private static final VertexAttributeType[] INSTANCE_ATTRIBUTES = {VertexAttributeType.COLOR,VertexAttributeType.UV_LIGHTMAP,VertexAttributeType.UV_OVERLAY};
    private static boolean failed;
    private static long submittedGroups, submittedInstances, uploads, reused, busy;

    private static final class Page {
        int vao, buffer, inputBuffer, indirectBuffer, capacity, count;
        long fence;
        int[] values;
        boolean available() {
            if (fence == 0) return true;
            int status = GL33.glClientWaitSync(fence, 0, 0L);
            if (status != GL33.GL_ALREADY_SIGNALED && status != GL33.GL_CONDITION_SATISFIED) return false;
            GL33.glDeleteSync(fence); fence = 0;
            return true;
        }
        boolean same(int count) {
            if (this.count != count || values == null) return false;
            for (int i = 0; i < count * WORDS; i++) if (values[i] != VALUES[i]) return false;
            return true;
        }
        void close() {
            if (fence != 0) GL33.glDeleteSync(fence);
            if (vao != 0) GL33.glDeleteVertexArrays(vao);
            if (buffer != 0) GL33.glDeleteBuffers(buffer);
            if (inputBuffer != 0) GL33.glDeleteBuffers(inputBuffer);
            if (indirectBuffer != 0) GL33.glDeleteBuffers(indirectBuffer);
            fence = 0; vao = buffer = inputBuffer = indirectBuffer = 0; values = null;
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
        // Decide shader capability only once per batch, not for every run.
        final boolean shaderless = Utilities.canUseCustomShader();
        if (!ENABLED || failed || (shaderless && OptimizedRenderer.renderingShadows() && !HELIX_SHADOW_INSTANCES) || GlStateTracker.isGl4ES()) {
            calls.forEach(ordinary); return;
        }
        if (!GL.getCapabilities().OpenGL33) { calls.forEach(ordinary); return; }
        // On Forge the unsafe @ModifyArgs Oculus bridge is not installed.
        // Without patched shader programs, grouping models only reorders and
        // checks thousands of calls before falling back to individual draws.
        if (!shaderless && !OculusInstanceRenderer.shaderBridgeReady()) {
            calls.forEach(ordinary);
            return;
        }
        // The incoming list may mix many materials. Trying to atlas the entire
        // batch fails as soon as even one incompatible part is present. Process
        // consecutive compatible material segments *in order*; each is either
        // drawn once via GPU multi-draw or via the existing instanced renderer.
        // This does not merge textures or alter transparent draw order.
        boolean atlasPossible=false;
        if(shaderless && SillonMegaEngine.GPU_DRIVEN
                && GL.getCapabilities().OpenGL43) {
            for(int begin=0;begin<calls.size();) {
                int end=begin+1;
                if(GpuDrivenMaterialAtlas.candidate(calls.get(begin))) {
                    while(end<calls.size() && GpuDrivenMaterialAtlas.sameMaterial(
                            calls.get(begin),calls.get(end)) && end-begin<1024)end++;
                    if(GpuDrivenMaterialAtlas.worthwhile(calls,begin,end)) {
                        atlasPossible=true;break;
                    }
                }
                begin=end;
            }
        }
        // Avoid fragmenting the previous instancing/reorder windows for worlds
        // that contain no compatible, sufficiently large heterogeneous atlas.
        if(atlasPossible) {
            for(int start=0;start<calls.size();) {
                int end=start+1;
                if(GpuDrivenMaterialAtlas.candidate(calls.get(start))) {
                    while(end<calls.size()
                            && GpuDrivenMaterialAtlas.sameMaterial(calls.get(start),calls.get(end))
                            && end-start<1024)end++;
                    if(GpuDrivenMaterialAtlas.draw(calls.subList(start,end))) {
                        GlVertexAttributeCache.invalidate();
                        start=end;
                        continue;
                    }
                } else {
                    while(end<calls.size() && !GpuDrivenMaterialAtlas.candidate(calls.get(end)))end++;
                }
                drawExisting(calls.subList(start,end),ordinary,shaderless);
                start=end;
            }
            return;
        }
        drawExisting(calls,ordinary,shaderless);
    }

    private static void drawExisting(List<?> calls,Consumer<Object> ordinary,boolean shaderless) {
        if(calls.isEmpty())return;
        // Conservative disjoint opaque screen rectangles only; unknown bounds do not move.
        boolean allowReorder=shaderless || (SillonMegaEngine.SHADER_REORDER
            && OculusInstanceRenderer.shaderBridgeReady());
        List<?> ordered=allowReorder && !"false".equalsIgnoreCase(System.getProperty("mtr.next.grouping","true"))
            ? BoundedDrawGrouping.reorder(calls,InstancedModelRenderer::compatible,
                call->call instanceof RenderCallData data?data.mtr$screenBounds():null,
                SillonMegaEngine.WINDOW) : calls;
        ConsecutiveRuns.visit(ordered, shaderless ? MAX_INSTANCES : InstanceShaderSource.CAPACITY, InstancedModelRenderer::compatible, (start, end) -> {
            boolean drawn = false;
            if (end - start >= SillonMegaEngine.GROUP_MIN && ordered.get(start) instanceof RenderCallData) {
                try { drawn = shaderless ? drawRun(ordered, start, end) : OculusInstanceRenderer.draw(ordered, start, end); }
                catch (RuntimeException error) {
                    failed = true;
                    Init.LOGGER.error("MTR Next instances disabled for this resource generation; ordinary draws restored", error);
                } finally { GlVertexAttributeCache.invalidate(); }
            }
            if (!drawn) for (int i = start; i < end; i++) ordinary.accept(ordered.get(i));
        });
    }

    private static boolean eligible(RenderCallData call) {
        VertexArray array = call.mtr$array();
        VertexAttributeState state = call.mtr$attributes();
        if (RegionVertexRanges.contains(array) || !call.mtr$visible()) return false;
        if (!((Object) array instanceof VertexArrayIdentity) || ((VertexArrayIdentity) (Object) array).mtr$vaoId() == 0 || state.matrix4f == null) return false;
        if (array.materialProperties.translucent || array.materialProperties.cutoutHack || !array.materialProperties.writeDepthBuf) return false;
        if (state.position != null || state.normal != null || state.textureU != null || state.textureV != null) return false;
        for (VertexAttributeType type : TYPES) {
            VertexAttributeSource source = array.mapping.sources.get(type);
            if (type == VertexAttributeType.MATRIX_MODEL && source != VertexAttributeSource.GLOBAL) return false;
            if (source != VertexAttributeSource.GLOBAL && source != VertexAttributeSource.VERTEX_BUFFER) return false;
        }
        for (VertexAttributeType type : INSTANCE_ATTRIBUTES)
            if(InstanceValues.varying(array,type) && InstanceValues.value(call,type)==null)return false;
        return array.materialProperties.vertexAttributeState.matrix4f == null;
    }
    private static boolean compatible(Object left, Object right) {
        if (!(left instanceof RenderCallData) || !(right instanceof RenderCallData)) return false;
        RenderCallData a = (RenderCallData) left, b = (RenderCallData) right;
        if (a.mtr$array() != b.mtr$array() || a.mtr$family() != b.mtr$family() || !eligible(a) || !eligible(b)) return false;
        return true; // Different global colors/light/overlay are encoded for each instance.
    }

    private static boolean drawRun(List<?> calls, int start, int end) {
        RenderCallData first = (RenderCallData) calls.get(start);
        if (!eligible(first)) return false;
        VertexArray source = first.mtr$array();
        int count = end - start;
        SCRATCH.clear();FLOATS.clear();
        for (int i = start; i < end; i++) {
            // Utilities.store uses absolute writes; use a 16-float slice for each matrix.
            FloatBuffer matrix = MATRIX_VIEWS[i - start];
            matrix.clear();
            Matrix4f value = ((RenderCallData) calls.get(i)).mtr$attributes().matrix4f;
            Utilities.store(value, matrix);
            RenderCallData call=(RenderCallData)calls.get(i);
            int base=(i-start)*MATRIX_BYTES;
            Integer color=InstanceValues.value(call,VertexAttributeType.COLOR);
            int rgba=color==null?-1:color;
            SCRATCH.put(base+64,(byte)(rgba>>>24));SCRATCH.put(base+65,(byte)(rgba>>>16));SCRATCH.put(base+66,(byte)(rgba>>>8));SCRATCH.put(base+67,(byte)rgba);
            Integer light=InstanceValues.value(call,VertexAttributeType.UV_LIGHTMAP),overlay=InstanceValues.value(call,VertexAttributeType.UV_OVERLAY);
            SCRATCH.putShort(base+68,(short)(light==null?0:light>>>16));SCRATCH.putShort(base+70,(short)(light==null?0:(int)light));
            SCRATCH.putShort(base+72,(short)(overlay==null?0:overlay>>>16));SCRATCH.putShort(base+74,(short)(overlay==null?0:(int)overlay));
        }
        SCRATCH.asIntBuffer().get(0, VALUES, 0, count * WORDS);
        // Real GPU culling only operates on sufficiently large opaque,
        // shaderless train/rail model groups. A missing mesh bound, GL4.3 or
        // failed shader always returns to the original instanced renderer.
        final boolean gpuCandidate=GpuDrivenInstanceRenderer.supported(count,first);
        final float[] gpuBounds=gpuCandidate?ModelPartVisibility.gpuBounds(source):null;
        Slot slot = SLOTS.get(source);
        if (slot == null) {
            if (SLOTS.size() == MAX_SLOTS) {
                Iterator<Slot> iterator = SLOTS.values().iterator(); Slot victim = iterator.next(); iterator.remove(); victim.close();
            }
            slot = new Slot(source); SLOTS.put(source, slot);
        }
        Page page = null;
        // GPU output depends on camera orientation and compute compaction.
        // A previous CPU/VBO cache hit may never substitute for that output.
        if (!gpuCandidate)
            for (Page candidate : slot.pages) if (candidate != null && candidate.same(count)) {
                page = candidate; reused++; break;
            }
        if (page == null) {
            for (int i = 0; i < PAGES; i++) {
                if (slot.pages[i] == null) slot.pages[i] = createPage(source);
                if (slot.pages[i].available()) { page = slot.pages[i]; break; }
            }
            if (page == null) { busy++; return false; }
            if (!gpuCandidate) {
                GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, page.buffer);
                int bytes = count * MATRIX_BYTES;
                if (page.capacity < bytes) {
                    page.capacity = Integer.highestOneBit(bytes - 1) << 1;
                    GL33.glBufferData(GL33.GL_ARRAY_BUFFER, page.capacity, GL33.GL_STREAM_DRAW);
                }
                SCRATCH.position(0).limit(bytes);
                GL33.glBufferSubData(GL33.GL_ARRAY_BUFFER, 0, SCRATCH);
                if (page.values == null || page.values.length < count * WORDS) page.values = new int[page.capacity / Integer.BYTES];
                System.arraycopy(VALUES, 0, page.values, 0, count * WORDS); page.count = count; uploads++;
            }
        }
        GL33.glBindVertexArray(page.vao);
        first.mtr$attributes().apply();
        source.materialProperties.vertexAttributeState.apply();
        RenderPipelineMetrics.drawing(first.mtr$family());
        if (gpuCandidate) {
            if(page.inputBuffer==0)page.inputBuffer=GL33.glGenBuffers();
            if(page.indirectBuffer==0)page.indirectBuffer=GL33.glGenBuffers();
            // The compute pass writes a compacted instance VBO and updates
            // DrawElementsIndirectCommand.instanceCount without CPU readback.
            // Compute replaces the VBO storage. Reset the CPU-upload
            // metadata even on a failed dispatch, before ordinary fallback.
            page.values=null;page.count=0;page.capacity=0;
            if(!GpuDrivenInstanceRenderer.draw(page.vao,page.inputBuffer,page.buffer,
                page.indirectBuffer,SCRATCH,count,source.indexBuffer.getVertexCount(),
                source.indexBuffer.indexType,gpuBounds))return false;
        } else {
            GL33.glDrawElementsInstanced(GL33.GL_TRIANGLES, source.indexBuffer.getVertexCount(),
                source.indexBuffer.indexType, 0L, count);
        }
        // A submitted draw must never be replayed if diagnostics/fence bookkeeping fails.
        try {
            RenderPipelineMetrics.instancedDraw(source.indexBuffer.getVertexCount(), count);
            if (page.fence != 0) GL33.glDeleteSync(page.fence);
            page.fence = GL33.glFenceSync(GL33.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            submittedGroups++; submittedInstances += count;
        } catch (RuntimeException error) { failed=true; Init.LOGGER.error("MTR instances retained submitted draw and disabled later groups",error); }
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
            VertexAttributeType[] attributes=INSTANCE_ATTRIBUTES;
            int[] offsets={64,68,72};
            for(int i=0;i<attributes.length;i++)if(InstanceValues.varying(source,attributes[i])){attributes[i].toggleAttributeArray(true);attributes[i].setupAttributePointer(MATRIX_BYTES,offsets[i]);GL33.glVertexAttribDivisor(attributes[i].location,1);}
            return page;
        } catch (RuntimeException | Error error) { page.close(); throw error; }
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(InstancedModelRenderer::clear); return; }
        SLOTS.values().forEach(Slot::close); SLOTS.clear(); failed = false; OculusInstanceRenderer.clear(); GpuDrivenInstanceRenderer.clear(); GpuDrivenMaterialAtlas.clear();
        submittedGroups = submittedInstances = uploads = reused = busy = 0;
    }
    public static void report() {
        if (submittedGroups != 0 || busy != 0) Init.LOGGER.info("MTR Next instances: groups={}, instances={}, uploads={}, reused={}, busy-fallbacks={}, resident-slots={}", submittedGroups, submittedInstances, uploads, reused, busy, SLOTS.size());
        GpuDrivenInstanceRenderer.report();
        GpuDrivenMaterialAtlas.report();
        submittedGroups = submittedInstances = uploads = reused = busy = 0;
    }
}
