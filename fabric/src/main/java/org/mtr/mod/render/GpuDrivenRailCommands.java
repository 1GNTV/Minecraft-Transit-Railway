package org.mtr.mod.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL43;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.mapper.MinecraftClientHelper;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mod.Init;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * GPU command-side culling for static opaque rail region spans.
 *
 * The existing immutable region meshes stay resident. Per-span local AABBs
 * are uploaded alongside indirect draw commands on scene selection changes.
 * A GL4.3 compute pass fills the indirect instanceCount (0 or 1), without
 * downloading results or rebuilding indices. The ordinary multi-draw path is
 * still used for shaders, shadow cascades, unsupported GL, and errors.
 *
 * This is a fine-grained *additional* conservative cull, not GPU ownership of
 * the full rail scene: CPU still discovers which regions belong to each pass.
 */
public final class GpuDrivenRailCommands {
    private static final int GROUP_SIZE=64;
    private static final int MIN_GROUP=Math.max(2,Math.min(4096,Integer.getInteger("mtr.mega.railComputeMin",16)));
    private static final String SHADER="""
        #version 430 core
        layout(local_size_x=64) in;
        struct Bounds { vec4 low; vec4 high; };
        layout(std430,binding=6) readonly buffer Extents { Bounds spans[]; };
        layout(std430,binding=7) buffer DrawCommands { uint words[]; };
        uniform uint mtr_Count;
        uniform vec3 mtr_Origin;
        uniform vec3 mtr_Eye;
        uniform vec3 mtr_Forward;
        uniform float mtr_Distance;
        void main() {
            uint id=gl_GlobalInvocationID.x;
            if(id>=mtr_Count)return;
            Bounds b=spans[id];
            // Unknown bounds fail open: shaders and add-ons must not lose meshes.
            if(b.low.x>1.0e20){words[id*5u+1u]=1u;return;}
            vec3 center=(b.low.xyz+b.high.xyz)*0.5+mtr_Origin;
            vec3 halfExtent=(b.high.xyz-b.low.xyz)*0.5;
            float radius=length(halfExtent)+16.0;
            vec3 delta=center-mtr_Eye;
            float d2=dot(delta.xz,delta.xz);
            bool outside=d2>(mtr_Distance+radius)*(mtr_Distance+radius);
            // The main MTR 32-block near bubble is never camera-forward culled.
            if(!outside && d2>1024.0)
                outside=dot(delta,mtr_Forward)<-radius;
            words[id*5u+1u]=outside?0u:1u;
        }
        """;
    private static int program=-1,countLoc,originLoc,eyeLoc,forwardLoc,distanceLoc;
    private static boolean failed;
    private static long dispatches;
    private GpuDrivenRailCommands(){}

    public static boolean supported(int count) {
        return SillonMegaEngine.GPU_DRIVEN && count>=MIN_GROUP && !failed
            && !"false".equalsIgnoreCase(System.getProperty("mtr.mega.railCompute","true"))
            && Utilities.canUseCustomShader() && !OptimizedRenderer.renderingShadows()
            && !GlStateTracker.isGl4ES() && GL.getCapabilities().OpenGL43;
    }

    public static ByteBuffer encode(List<RegionVertexRanges.Span> spans) {
        ByteBuffer data=ByteBuffer.allocateDirect(spans.size()*32).order(ByteOrder.nativeOrder());
        for(var s:spans) {
            if(s.bounded()) {
                data.putFloat(s.x0()).putFloat(s.y0()).putFloat(s.z0()).putFloat(0);
                data.putFloat(s.x1()).putFloat(s.y1()).putFloat(s.z1()).putFloat(0);
            } else {
                data.putFloat(Float.POSITIVE_INFINITY).putFloat(0).putFloat(0).putFloat(0);
                data.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
            }
        }
        data.flip();
        return data;
    }

    private static boolean initialize() {
        if(failed)return false;
        if(program>0)return true;
        int shader=0,p=0;
        try {
            shader=GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL43.glShaderSource(shader,SHADER);
            GL43.glCompileShader(shader);
            if(GL43.glGetShaderi(shader,GL43.GL_COMPILE_STATUS)==0)
                throw new IllegalStateException("Rail culling shader: "+GL43.glGetShaderInfoLog(shader));
            p=GL43.glCreateProgram();
            GL43.glAttachShader(p,shader);
            GL43.glLinkProgram(p);
            if(GL43.glGetProgrami(p,GL43.GL_LINK_STATUS)==0)
                throw new IllegalStateException("Rail culling program: "+GL43.glGetProgramInfoLog(p));
            program=p;
            countLoc=GL43.glGetUniformLocation(p,"mtr_Count");
            originLoc=GL43.glGetUniformLocation(p,"mtr_Origin");
            eyeLoc=GL43.glGetUniformLocation(p,"mtr_Eye");
            forwardLoc=GL43.glGetUniformLocation(p,"mtr_Forward");
            distanceLoc=GL43.glGetUniformLocation(p,"mtr_Distance");
            return true;
        } catch(RuntimeException error) {
            failed=true;
            Init.LOGGER.error("Sillon rail compute culler unavailable; classic indexed draws restored",error);
            if(p!=0)GL43.glDeleteProgram(p);
            return false;
        } finally {if(shader!=0)GL43.glDeleteShader(shader);}
    }

    /**
     * Returns false before a draw if an indirect command buffer cannot safely
     * be updated; caller then executes the ordinary multi-draw renderer.
     */
    public static boolean cull(int commands,int boundsBuffer,int count,
                               float originX,float originY,float originZ) {
        if(boundsBuffer==0||!supported(count)||!initialize())return false;
        var camera=MinecraftClient.getInstance().getGameRendererMapped().getCamera();
        var eye=camera.getPos();
        float yaw=(float)Math.toRadians(camera.getYaw());
        float pitch=(float)Math.toRadians(camera.getPitch());
        var basisX=new org.mtr.mapping.holder.Vector3d(1,0,0).rotateY(yaw).rotateX(pitch);
        var basisY=new org.mtr.mapping.holder.Vector3d(0,1,0).rotateY(yaw).rotateX(pitch);
        var basisZ=new org.mtr.mapping.holder.Vector3d(0,0,1).rotateY(yaw).rotateX(pitch);
        int previousProgram=GL43.glGetInteger(GL43.GL_CURRENT_PROGRAM);
        int prevSSBO=GL43.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int prev6=GL43.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,6);
        int prev7=GL43.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,7);
        try {
            GL43.glUseProgram(program);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,6,boundsBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,7,commands);
            GL43.glUniform1ui(countLoc,count);
            GL43.glUniform3f(originLoc,originX,originY,originZ);
            GL43.glUniform3f(eyeLoc,(float)eye.getXMapped(),(float)eye.getYMapped(),(float)eye.getZMapped());
            GL43.glUniform3f(forwardLoc,(float)basisX.getZMapped(),(float)basisY.getZMapped(),(float)basisZ.getZMapped());
            GL43.glUniform1f(distanceLoc,MinecraftClientHelper.getRenderDistance()*16F);
            GL43.glDispatchCompute((count+GROUP_SIZE-1)/GROUP_SIZE,1,1);
            GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT|GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            dispatches++;
            return true;
        } catch(RuntimeException error) {
            failed=true;
            Init.LOGGER.error("Sillon rail GPU command generation failed; ordinary draws restored",error);
            return false;
        } finally {
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,6,prev6);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,7,prev7);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,prevSSBO);
            GL43.glUseProgram(previousProgram);
        }
    }

    public static void clear() {
        if(program>0 && GL.getCapabilities().OpenGL43)GL43.glDeleteProgram(program);
        program=-1;failed=false;dispatches=0;
    }
    public static void report() {
        if(dispatches>0)Init.LOGGER.info("Sillon GPU rail commands: compute dispatches={}",dispatches);
        dispatches=0;
    }
}
