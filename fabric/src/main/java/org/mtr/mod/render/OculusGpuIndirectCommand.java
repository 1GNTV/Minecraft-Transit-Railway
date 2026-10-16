package org.mtr.mod.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL43;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mod.Init;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Optional GPU-issued draw path for already patched Oculus/Iris uniform-array
 * instancing. The unmodified 0..N-1 gl_InstanceID mapping is preserved.
 *
 * GPU compute checks conservative clip bounds for each instance and updates
 * ONE indirect draw's instanceCount to either N or 0. We do not compact
 * instances here: that would invalidate the already uploaded Iris uniform
 * arrays (mtr_ModelViews[gl_InstanceID], tint/light/overlay).
 *
 * A true cross-material Oculus atlas needs shader-pack-specific per-draw SSBO
 * transformation and sampler routing; we deliberately never claim that this
 * class provides it. Entirely optional, GL4.3, not shadows or GL4ES.
 */
public final class OculusGpuIndirectCommand {
    private static final boolean ENABLED=SillonMegaEngine.GPU_DRIVEN
        && !"false".equalsIgnoreCase(System.getProperty("mtr.mega.oculusIndirect","true"));
    private static final ByteBuffer RECORDS=ByteBuffer.allocateDirect(InstanceShaderSource.CAPACITY*64)
        .order(ByteOrder.nativeOrder());
    private static final ByteBuffer BOUNDS=ByteBuffer.allocateDirect(InstanceShaderSource.CAPACITY*32)
        .order(ByteOrder.nativeOrder());
    private static final ByteBuffer COMMAND=ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder());
    private static final FloatBuffer CLIP=ByteBuffer.allocateDirect(64)
        .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final String SOURCE="""
        #version 430 core
        layout(local_size_x=32) in;
        layout(std430,binding=3) readonly buffer Records { float matrices[]; };
        layout(std430,binding=4) readonly buffer Extents { vec4 bounds[]; };
        layout(std430,binding=5) buffer Command { uint words[]; };
        uniform mat4 mtr_ViewProjection;
        uniform uint mtr_Count;
        void main() {
            uint i=gl_GlobalInvocationID.x;
            if(i>=mtr_Count)return;
            uint base=i*16u;
            mat4 model=mat4(
                vec4(matrices[base],matrices[base+1u],matrices[base+2u],matrices[base+3u]),
                vec4(matrices[base+4u],matrices[base+5u],matrices[base+6u],matrices[base+7u]),
                vec4(matrices[base+8u],matrices[base+9u],matrices[base+10u],matrices[base+11u]),
                vec4(matrices[base+12u],matrices[base+13u],matrices[base+14u],matrices[base+15u])
            );
            vec3 lo=bounds[i*2u].xyz,hi=bounds[i*2u+1u].xyz;
            mat4 clip=mtr_ViewProjection*model;
            uint outside=63u;
            for(uint c=0u;c<8u;c++){
                vec3 v=vec3((c&1u)==0u?lo.x:hi.x,
                            (c&2u)==0u?lo.y:hi.y,
                            (c&4u)==0u?lo.z:hi.z);
                vec4 p=clip*vec4(v,1.0);
                uint mask=(p.x < -p.w?1u:0u)|(p.x>p.w?2u:0u)
                          |(p.y < -p.w?4u:0u)|(p.y>p.w?8u:0u)
                          |(p.z < -p.w?16u:0u)|(p.z>p.w?32u:0u);
                outside&=mask;
            }
            // Preserve Iris gl_InstanceID -> uniform index mapping by
            // keeping every instance when *any* one is visible.
            if(outside==0u)atomicOr(words[1],mtr_Count);
        }
        """;
    private static int program=-1,countLoc,clipLoc,input,extents,indirect;
    private static boolean failed;
    private static long commands,considered;
    private OculusGpuIndirectCommand(){}
    public static boolean supported(int instances,RenderCallData first) {
        return ENABLED && !failed && instances>=4
            && first.mtr$family()==RenderPipelineMetrics.Family.TRAIN
            && OculusInstanceRenderer.shaderBridgeReady()
            // Shadow passes retain exact shader-pack matrices and depth state.
            && !org.mtr.mapping.mapper.OptimizedRenderer.renderingShadows()
            && ModelPartVisibility.gpuBounds(first.mtr$array())!=null
            && !GlStateTracker.isGl4ES()
            && GL.getCapabilities().OpenGL43;
    }
    private static boolean initialize() {
        if(program>0)return true;
        int s=0,p=0;
        try {
            s=GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL43.glShaderSource(s,SOURCE);
            GL43.glCompileShader(s);
            if(GL43.glGetShaderi(s,GL43.GL_COMPILE_STATUS)==0)
                throw new IllegalStateException("Oculus indirect compute: "+GL43.glGetShaderInfoLog(s));
            p=GL43.glCreateProgram();
            GL43.glAttachShader(p,s);
            GL43.glLinkProgram(p);
            if(GL43.glGetProgrami(p,GL43.GL_LINK_STATUS)==0)
                throw new IllegalStateException("Oculus indirect link: "+GL43.glGetProgramInfoLog(p));
            program=p;
            countLoc=GL43.glGetUniformLocation(p,"mtr_Count");
            clipLoc=GL43.glGetUniformLocation(p,"mtr_ViewProjection");
            input=GL43.glGenBuffers();extents=GL43.glGenBuffers();indirect=GL43.glGenBuffers();
            return true;
        } catch(RuntimeException ex) {
            failed=true;
            Init.LOGGER.error("Sillon Oculus indirect unavailable: normal Oculus instancing retained",ex);
            if(p!=0)GL43.glDeleteProgram(p);
            return false;
        } finally {if(s!=0)GL43.glDeleteShader(s);}
    }
    /** true if draw command was issued, including GPU-rejected (instanceCount 0) groups */
    public static boolean draw(List<?> calls,int start,int end,int indexCount,int indexType) {
        int count=end-start;
        if(!(calls.get(start) instanceof RenderCallData first)||!supported(count,first)||!initialize())
            return false;
        RECORDS.clear();BOUNDS.clear();
        for(int i=start;i<end;i++) {
            RenderCallData c=(RenderCallData)calls.get(i);
            FloatBuffer matrix=RECORDS.asFloatBuffer().slice((i-start)*16,16);
            org.mtr.mapping.render.tool.Utilities.store(c.mtr$attributes().matrix4f,matrix);
            float[] bounds=ModelPartVisibility.gpuBounds(c.mtr$array());
            if(bounds==null)return false;
            for(int j=0;j<3;j++)BOUNDS.putFloat(bounds[j]);
            BOUNDS.putFloat(0);
            for(int j=3;j<6;j++)BOUNDS.putFloat(bounds[j]);
            BOUNDS.putFloat(0);
        }
        RECORDS.position(0).limit(count*64);BOUNDS.flip();
        COMMAND.clear();
        COMMAND.putInt(indexCount).putInt(0).putInt(0).putInt(0).putInt(0).flip();
        int previousProgram=GL43.glGetInteger(GL43.GL_CURRENT_PROGRAM);
        int previousSSBO=GL43.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousIndirect=GL43.glGetInteger(GL43.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int[] bindings=new int[3];
        for(int i=0;i<3;i++)bindings[i]=GL43.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i+3);
        boolean submitted=false;
        try {
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,input);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,RECORDS,GL43.GL_STREAM_DRAW);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,extents);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,BOUNDS,GL43.GL_STREAM_DRAW);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,indirect);
            GL43.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER,COMMAND,GL43.GL_STREAM_DRAW);
            CLIP.clear();
            new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
                .mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).get(CLIP);
            CLIP.position(0);
            GL43.glUseProgram(program);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,3,input);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,4,extents);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,5,indirect);
            GL43.glUniformMatrix4fv(clipLoc,false,CLIP);
            GL43.glUniform1ui(countLoc,count);
            GL43.glDispatchCompute(1,1,1);
            GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT|GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            GL43.glUseProgram(previousProgram);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,indirect);
            GL43.glDrawElementsIndirect(GL43.GL_TRIANGLES,indexType,0L);
            submitted=true;
            commands++;considered+=count;
        } catch(RuntimeException exception) {
            failed=true;Init.LOGGER.error("Oculus GPU indirect disabled after GL failure",exception);
        } finally {
            for(int i=0;i<3;i++)GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i+3,bindings[i]);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,previousSSBO);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,previousIndirect);
            GL43.glUseProgram(previousProgram);
        }
        return submitted;
    }
    public static void clear() {
        if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(OculusGpuIndirectCommand::clear);
            return;
        }
        if(program>0)GL43.glDeleteProgram(program);
        for(int id:new int[]{input,extents,indirect})if(id!=0)GL43.glDeleteBuffers(id);
        program=-1;input=extents=indirect=0;failed=false;commands=considered=0;
    }
    public static void report(){
        if(commands>0)Init.LOGGER.info("Sillon Oculus indirect: commands={}, instances-tested={}",commands,considered);
        commands=considered=0;
    }
}
