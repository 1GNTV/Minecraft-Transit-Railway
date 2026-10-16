package org.mtr.mod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL43;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mod.Init;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL 4.3 GPU-owned instance selection + indirect draw for opaque MTR
 * models. The compute shader consumes the exact packed 76-byte instance
 * records used by InstancedModelRenderer, tests 8 conservative clip corners,
 * atomically compacts survivors into a vertex-attribute buffer and updates the
 * DrawElementsIndirectCommand.instanceCount on the GPU.
 *
 * This is NOT a generic cross-material train renderer: one immutable VAO,
 * material, index buffer and shader program are bound per group. It must never
 * run under Oculus/unpatched shaders, translucent parts or GL4ES.
 *
 * Render-thread only. All buffer names are owned by the caller's per-VAO page.
 */
public final class GpuDrivenInstanceRenderer {
    private static final int RECORD_FLOATS=19, LOCAL_SIZE=64;
    private static final int MIN_GROUP=Math.max(8,Math.min(1024,Integer.getInteger("mtr.mega.computeMinInstances",48)));
    private static final ByteBuffer COMMAND_BYTES=ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder());
    private static final FloatBuffer CLIP_BYTES=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final String SHADER="""
        #version 430 core
        layout(local_size_x=64) in;
        layout(std430,binding=0) readonly buffer Incoming { uint inputData[]; };
        layout(std430,binding=1) writeonly buffer Outgoing { uint outputData[]; };
        layout(std430,binding=2) buffer Indirect { uint command[]; };
        uniform uint mtr_Count;
        uniform mat4 mtr_ViewProjection;
        uniform vec3 mtr_Min, mtr_Max;
        void main() {
            uint id=gl_GlobalInvocationID.x;
            if(id>=mtr_Count) return;
            uint base=id*19u;
            mat4 model=mat4(
                vec4(uintBitsToFloat(inputData[base]),uintBitsToFloat(inputData[base+1u]),uintBitsToFloat(inputData[base+2u]),uintBitsToFloat(inputData[base+3u])),
                vec4(uintBitsToFloat(inputData[base+4u]),uintBitsToFloat(inputData[base+5u]),uintBitsToFloat(inputData[base+6u]),uintBitsToFloat(inputData[base+7u])),
                vec4(uintBitsToFloat(inputData[base+8u]),uintBitsToFloat(inputData[base+9u]),uintBitsToFloat(inputData[base+10u]),uintBitsToFloat(inputData[base+11u])),
                vec4(uintBitsToFloat(inputData[base+12u]),uintBitsToFloat(inputData[base+13u]),uintBitsToFloat(inputData[base+14u]),uintBitsToFloat(inputData[base+15u]))
            );
            mat4 clip=mtr_ViewProjection*model;
            uint common=63u;
            for(uint i=0u;i<8u;i++){
                vec3 corner=vec3((i&1u)==0u?mtr_Min.x:mtr_Max.x,
                    (i&2u)==0u?mtr_Min.y:mtr_Max.y,
                    (i&4u)==0u?mtr_Min.z:mtr_Max.z);
                vec4 p=clip*vec4(corner,1.0);
                uint mask=(p.x < -p.w?1u:0u) | (p.x > p.w?2u:0u)
                    | (p.y < -p.w?4u:0u) | (p.y > p.w?8u:0u)
                    | (p.z < -p.w?16u:0u) | (p.z > p.w?32u:0u);
                common&=mask;
            }
            if(common!=0u)return;
            uint dst=atomicAdd(command[1],1u)*19u;
            for(uint j=0u;j<19u;j++)outputData[dst+j]=inputData[base+j];
        }
        """;
    private static int program=-1, countLoc, matrixLoc, minLoc, maxLoc;
    private static boolean failed;
    private static long drawn, dispatched;
    private GpuDrivenInstanceRenderer() {}

    public static boolean potential(RenderCallData call) {
        return SillonMegaEngine.GPU_DRIVEN && SillonMegaEngine.INSTANCES
            && !"false".equalsIgnoreCase(System.getProperty("mtr.mega.computeCull","true"))
            && (call.mtr$family()==RenderPipelineMetrics.Family.TRAIN
             || call.mtr$family()==RenderPipelineMetrics.Family.RAIL)
            && Utilities.canUseCustomShader() && !OptimizedRenderer.renderingShadows()
            && ModelPartVisibility.gpuBounds(call.mtr$array())!=null;
    }

    public static boolean supported(int instances, RenderCallData first) {
        if(failed || instances<MIN_GROUP || !potential(first) || GlStateTracker.isGl4ES())
            return false;
        return GL.getCapabilities().OpenGL43;
    }

    private static boolean initialize() {
        if(failed) return false;
        if(program>0)return true;
        int shader=0, p=0;
        try {
            shader=GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL43.glShaderSource(shader,SHADER);
            GL43.glCompileShader(shader);
            if(GL43.glGetShaderi(shader,GL43.GL_COMPILE_STATUS)==0)
                throw new IllegalStateException("Sillon compute shader compile: "+GL43.glGetShaderInfoLog(shader));
            p=GL43.glCreateProgram();
            GL43.glAttachShader(p,shader);
            GL43.glLinkProgram(p);
            if(GL43.glGetProgrami(p,GL43.GL_LINK_STATUS)==0)
                throw new IllegalStateException("Sillon compute shader link: "+GL43.glGetProgramInfoLog(p));
            program=p;
            countLoc=GL43.glGetUniformLocation(p,"mtr_Count");
            matrixLoc=GL43.glGetUniformLocation(p,"mtr_ViewProjection");
            minLoc=GL43.glGetUniformLocation(p,"mtr_Min");
            maxLoc=GL43.glGetUniformLocation(p,"mtr_Max");
            return true;
        } catch (RuntimeException error) {
            failed=true;
            Init.LOGGER.error("Sillon GPU compute culling disabled; ordinary train draws preserved",error);
            if(p!=0)GL43.glDeleteProgram(p);
            return false;
        } finally {if(shader!=0)GL43.glDeleteShader(shader);}
    }

    /**
     * Return TRUE only if an indirect draw was submitted. The caller must
     * then treat the draw as complete and fence its per-VAO page; never replay
     * the ordinary renderer after a submitted command.
     */
    public static boolean draw(int vao,int inputBuffer,int outputBuffer,int indirectBuffer,
                               ByteBuffer raw,int count,int indices,int indexType,float[] bounds) {
        if(bounds==null || bounds.length!=6 || !initialize())return false;
        int previousProgram=GL43.glGetInteger(GL43.GL_CURRENT_PROGRAM);
        int previousVAO=GL43.glGetInteger(GL43.GL_VERTEX_ARRAY_BINDING);
        int previousArray=GL43.glGetInteger(GL43.GL_ARRAY_BUFFER_BINDING);
        int previousSSBO=GL43.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousIndirect=GL43.glGetInteger(GL43.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int[] previousIndexed=new int[3];
        for(int i=0;i<3;i++)previousIndexed[i]=GL43.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);
        boolean submitted=false;
        try {
            // Input, output and command buffers are distinct: parallel compute
            // threads never overwrite instance records still being read.
            ByteBuffer data=raw.duplicate().order(ByteOrder.nativeOrder());
            data.position(0).limit(count*RECORD_FLOATS*4);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,inputBuffer);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,data,GL43.GL_STREAM_DRAW);
            GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER,outputBuffer);
            GL43.glBufferData(GL43.GL_ARRAY_BUFFER,(long)count*RECORD_FLOATS*4,GL43.GL_STREAM_DRAW);
            COMMAND_BYTES.clear();
            COMMAND_BYTES.putInt(indices).putInt(0).putInt(0).putInt(0).putInt(0).flip();
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,indirectBuffer);
            GL43.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER,COMMAND_BYTES,GL43.GL_STREAM_DRAW);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,inputBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,outputBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,2,indirectBuffer);
            CLIP_BYTES.clear();
            new org.joml.Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).get(CLIP_BYTES);
            CLIP_BYTES.position(0);
            GL43.glUseProgram(program);
            GL43.glUniform1ui(countLoc,count);
            GL43.glUniformMatrix4fv(matrixLoc,false,CLIP_BYTES);
            GL43.glUniform3f(minLoc,bounds[0],bounds[1],bounds[2]);
            GL43.glUniform3f(maxLoc,bounds[3],bounds[4],bounds[5]);
            GL43.glDispatchCompute((count+LOCAL_SIZE-1)/LOCAL_SIZE,1,1);
            GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT
                | GL43.GL_SHADER_STORAGE_BARRIER_BIT
                | GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            // Reinstate the material shader before issuing the indirect draw.
            GL43.glUseProgram(previousProgram);
            GL43.glBindVertexArray(vao);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,indirectBuffer);
            GL43.glDrawElementsIndirect(GL43.GL_TRIANGLES,indexType,0L);
            submitted=true;
            drawn++;
            dispatched+=count;
        } catch(RuntimeException error) {
            failed=true;
            Init.LOGGER.error("Sillon GPU-driven instances disabled after GL failure",error);
        } finally {
            for(int i=0;i<3;i++)GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,previousIndexed[i]);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,previousSSBO);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,previousIndirect);
            GL43.glBindVertexArray(previousVAO);
            GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER,previousArray);
            GL43.glUseProgram(previousProgram);
        }
        return submitted;
    }

    public static void clear() {
        if(!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(GpuDrivenInstanceRenderer::clear);
            return;
        }
        if(program>0)GL43.glDeleteProgram(program);
        program=-1;
        failed=false;
        drawn=dispatched=0;
    }
    public static void report() {
        if(drawn>0)Init.LOGGER.info("Sillon GPU-driven: indirect groups={}, compute-tested instances={}",drawn,dispatched);
        drawn=dispatched=0;
    }
}
