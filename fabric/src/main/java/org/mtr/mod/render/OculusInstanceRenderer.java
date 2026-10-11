package org.mtr.mod.render;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL33;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.VertexAttributeType;
import org.mtr.mod.Init;
import java.nio.*;
import java.util.*;

/** Keeps the actual Oculus shader and its full apply; matrices, tint, light and overlay vary inside a draw. */
public final class OculusInstanceRenderer {
    private record Locations(int mode,int model,int inverse,int normal,int mask,int colors,int lights,int overlays) {}
    private static final Map<Integer,Locations> PROGRAMS=new HashMap<>();
    private static final FloatBuffer MODELS=buffer(8*16),INVERSES=buffer(8*16),NORMALS=buffer(8*9),ONE=buffer(16);
    private static final FloatBuffer COLORS=buffer(8*4);
    private static final IntBuffer LIGHTS=ByteBuffer.allocateDirect(8*8).order(ByteOrder.nativeOrder()).asIntBuffer(),OVERLAYS=ByteBuffer.allocateDirect(8*8).order(ByteOrder.nativeOrder()).asIntBuffer();
    private static final org.joml.Matrix4f MATRIX=new org.joml.Matrix4f();
    private static final org.joml.Matrix3f NORMAL=new org.joml.Matrix3f();
    private static boolean failed;
    private OculusInstanceRenderer() {}
    private static FloatBuffer buffer(int n){return ByteBuffer.allocateDirect(n*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
    public static InstanceShaderSource.Pair prepare(String v,String f){
        if ("false".equalsIgnoreCase(System.getProperty("mtr.next.oculusInstances","true")) || !RenderSystem.isOnRenderThread())return null;
        InstanceShaderSource.Pair pair=InstanceShaderSource.patch(v,f);if(pair==null)return null;
        int vs=0,fs=0,program=0;
        try{
            // Preflight before modifying the resource factory. A failed candidate leaves originals intact.
            vs=GL33.glCreateShader(GL33.GL_VERTEX_SHADER);GL33.glShaderSource(vs,pair.vertex());GL33.glCompileShader(vs);
            if(GL33.glGetShaderi(vs,GL33.GL_COMPILE_STATUS)==0)return null;
            fs=GL33.glCreateShader(GL33.GL_FRAGMENT_SHADER);GL33.glShaderSource(fs,pair.fragment());GL33.glCompileShader(fs);
            if(GL33.glGetShaderi(fs,GL33.GL_COMPILE_STATUS)==0)return null;
            program=GL33.glCreateProgram();GL33.glAttachShader(program,vs);GL33.glAttachShader(program,fs);GL33.glLinkProgram(program);
            if(GL33.glGetProgrami(program,GL33.GL_LINK_STATUS)==0)return null;
            return pair;
        }catch(RuntimeException error){Init.LOGGER.debug("MTR Oculus instancing preflight retained original shader",error);return null;}
        finally{if(program!=0)GL33.glDeleteProgram(program);if(vs!=0)GL33.glDeleteShader(vs);if(fs!=0)GL33.glDeleteShader(fs);}
    }
    public static boolean draw(List<?> calls,int start,int end){
        if(failed || end-start>8)return false;
        RenderCallData first=(RenderCallData)calls.get(start);
        int program=GL33.glGetInteger(GL33.GL_CURRENT_PROGRAM);
        if(program==0)return false;
        Locations locations=PROGRAMS.computeIfAbsent(program,p->new Locations(GL33.glGetUniformLocation(p,"mtr_Instanced"),GL33.glGetUniformLocation(p,"mtr_ModelViews[0]"),GL33.glGetUniformLocation(p,"mtr_ModelViewInverses[0]"),GL33.glGetUniformLocation(p,"mtr_NormalMatrices[0]"),GL33.glGetUniformLocation(p,"mtr_AttributeMask"),GL33.glGetUniformLocation(p,"mtr_Colors[0]"),GL33.glGetUniformLocation(p,"mtr_Lights[0]"),GL33.glGetUniformLocation(p,"mtr_Overlays[0]")));
        if(locations.mode<0 || locations.model<0)return false;

        MODELS.clear();INVERSES.clear();NORMALS.clear();COLORS.clear();LIGHTS.clear();OVERLAYS.clear();
        int mask=0;
        if(InstanceValues.varying(first.mtr$array(),VertexAttributeType.COLOR))mask|=1;
        if(InstanceValues.varying(first.mtr$array(),VertexAttributeType.UV_LIGHTMAP))mask|=2;
        if(InstanceValues.varying(first.mtr$array(),VertexAttributeType.UV_OVERLAY))mask|=4;
        for(int i=start;i<end;i++){
            RenderCallData call=(RenderCallData)calls.get(i);
            Integer color=InstanceValues.value(call,VertexAttributeType.COLOR),light=InstanceValues.value(call,VertexAttributeType.UV_LIGHTMAP),overlay=InstanceValues.value(call,VertexAttributeType.UV_OVERLAY);
            if(((mask&1)!=0 && color==null) || ((mask&2)!=0 && light==null) || ((mask&4)!=0 && overlay==null))return false;
            int rgba=color==null?-1:color,uv=light==null?0:light,ov=overlay==null?0:overlay;
            COLORS.put((rgba>>>24&255)/255f).put((rgba>>>16&255)/255f).put((rgba>>>8&255)/255f).put((rgba&255)/255f);
            LIGHTS.put((short)(uv>>>16)).put((short)uv);OVERLAYS.put((short)(ov>>>16)).put((short)ov);
            ONE.clear();Utilities.store(call.mtr$attributes().matrix4f,ONE);
            MATRIX.set(ONE);float determinant=MATRIX.determinant();
            if(!Float.isFinite(determinant) || Math.abs(determinant)<1e-12)return false;
            MATRIX.get((i-start)*16,MODELS);
            MATRIX.invert().get((i-start)*16,INVERSES);
            NORMAL.set(MATRIX).transpose().get((i-start)*9,NORMALS);
        }
        first.mtr$array().bind();first.mtr$attributes().apply();first.mtr$array().materialProperties.vertexAttributeState.apply();
        if(GL33.glGetInteger(GL33.GL_CURRENT_PROGRAM)!=program)return false;
        int count=end-start;boolean submitted=false;
        try{
            MODELS.limit(count*16);INVERSES.limit(count*16);NORMALS.limit(count*9);
            GL33.glUniformMatrix4fv(locations.model,false,MODELS);
            if(locations.inverse>=0)GL33.glUniformMatrix4fv(locations.inverse,false,INVERSES);
            if(locations.normal>=0)GL33.glUniformMatrix3fv(locations.normal,false,NORMALS);
            if(locations.mask>=0)GL33.glUniform1i(locations.mask,mask);
            COLORS.flip();LIGHTS.flip();OVERLAYS.flip();
            if(locations.colors>=0)GL33.glUniform4fv(locations.colors,COLORS);
            if(locations.lights>=0)GL33.glUniform2iv(locations.lights,LIGHTS);
            if(locations.overlays>=0)GL33.glUniform2iv(locations.overlays,OVERLAYS);
            GL33.glUniform1i(locations.mode,1);
            RenderPipelineMetrics.drawing(first.mtr$family());
            GL33.glDrawElementsInstanced(GL33.GL_TRIANGLES,first.mtr$array().indexBuffer.getVertexCount(),first.mtr$array().indexBuffer.indexType,0L,count);
            submitted=true;
            RenderPipelineMetrics.instancedDraw(first.mtr$array().indexBuffer.getVertexCount(),count);
        }catch(RuntimeException error){failed=true;Init.LOGGER.error("MTR Oculus instances reverted to ordinary draws",error);}
        finally{GL33.glUniform1i(locations.mode,0);}
        return submitted;
    }
    public static void clear(){PROGRAMS.clear();failed=false;}
    public static void invalidatePrograms(){PROGRAMS.clear();failed=false;}
}
