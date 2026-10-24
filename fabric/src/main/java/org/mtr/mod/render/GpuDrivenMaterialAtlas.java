package org.mtr.mod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL43;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.tool.GlStateTracker;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mapping.render.vertex.VertexAttributeSource;
import org.mtr.mapping.render.vertex.VertexAttributeType;
import org.mtr.mod.Init;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-model GPU material atlas for TRAIN/RAIL families (OpenGL 4.3).
 *
 * Compatible, opaque models with the *same material instance and identical
 * vertex layout* are copied from existing GPU VBO/IBOs into an immutable
 * shared atlas using glCopyBufferSubData (no mesh readback). Model matrices,
 * packed tint/light/overlay and conservative local AABBs are uploaded as a
 * per-pass command stream. A compute shader sets instanceCount=0/1 for every
 * DrawElementsIndirectCommand, and glMultiDrawElementsIndirect draws all
 * heterogeneous geometries with ONE bound material and ONE bound atlas VAO.
 *
 * Indirect baseVertex selects each original mesh in the atlas, baseInstance
 * selects its 76-byte instance record. No global material, shader or texture
 * order may change. If ANY source cannot be handled, the entire batch falls
 * back without partially submitting atlas calls.
 *
 * Source resources may be replaced by reload; InstancedModelRenderer.clear()
 * owns this cache lifetime. Deliberately opt-in until GPU/driver validation.
 */
public final class GpuDrivenMaterialAtlas {
    private static final int INSTANCE_BYTES=76;
    private static final int WORDS=19;
    private static final int LIMIT_CALLS=Math.max(64,Math.min(4096,Integer.getInteger("mtr.mega.atlasMaxDraws",1024)));
    private static final int MIN_CALLS=Math.max(4,Math.min(LIMIT_CALLS,Integer.getInteger("mtr.mega.atlasMinDraws",24)));
    private static final int MAX_ATLASES=12, MAX_SOURCES=64, MAX_VERTEX_BYTES=32*1024*1024;
    private static final int MAX_INDEX_BYTES=16*1024*1024;
    private static final boolean ENABLED=SillonMegaEngine.GPU_DRIVEN
        && Boolean.getBoolean("mtr.mega.materialAtlas");
    private static final VertexAttributeType[] TYPES=VertexAttributeType.values();
    private static final VertexAttributeType[] VARYING={
        VertexAttributeType.COLOR,VertexAttributeType.UV_LIGHTMAP,VertexAttributeType.UV_OVERLAY
    };
    private static final ByteBuffer RECORDS=ByteBuffer.allocateDirect(LIMIT_CALLS*INSTANCE_BYTES)
        .order(ByteOrder.nativeOrder());
    private static final FloatBuffer MATRIX_VIEWS=RECORDS.asFloatBuffer();
    private static final ByteBuffer BOUNDS=ByteBuffer.allocateDirect(LIMIT_CALLS*32)
        .order(ByteOrder.nativeOrder());
    private static final ByteBuffer COMMANDS=ByteBuffer.allocateDirect(LIMIT_CALLS*20)
        .order(ByteOrder.nativeOrder());
    private static final FloatBuffer CLIP=ByteBuffer.allocateDirect(16*4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final Map<Key,Atlas> ATLASES=new LinkedHashMap<>(16,.75F,true);
    private static boolean failed;
    private static long atlasedCalls, atlasedDraws, misses;

    private static final String COMPUTE="""
        #version 430 core
        layout(local_size_x=64) in;
        layout(std430,binding=0) readonly buffer Records { uint words[]; };
        layout(std430,binding=1) readonly buffer Extents { vec4 extents[]; };
        layout(std430,binding=2) buffer Commands { uint commands[]; };
        uniform uint mtr_Count;
        uniform mat4 mtr_ViewProjection;
        void main() {
            uint id=gl_GlobalInvocationID.x;
            if(id>=mtr_Count)return;
            uint n=id*19u;
            mat4 m=mat4(
                vec4(uintBitsToFloat(words[n]),uintBitsToFloat(words[n+1u]),
                     uintBitsToFloat(words[n+2u]),uintBitsToFloat(words[n+3u])),
                vec4(uintBitsToFloat(words[n+4u]),uintBitsToFloat(words[n+5u]),
                     uintBitsToFloat(words[n+6u]),uintBitsToFloat(words[n+7u])),
                vec4(uintBitsToFloat(words[n+8u]),uintBitsToFloat(words[n+9u]),
                     uintBitsToFloat(words[n+10u]),uintBitsToFloat(words[n+11u])),
                vec4(uintBitsToFloat(words[n+12u]),uintBitsToFloat(words[n+13u]),
                     uintBitsToFloat(words[n+14u]),uintBitsToFloat(words[n+15u]))
            );
            vec3 low=extents[id*2u].xyz, high=extents[id*2u+1u].xyz;
            mat4 clip=mtr_ViewProjection*m;
            uint outside=63u;
            for(uint i=0u;i<8u;i++){
                vec3 p=vec3((i&1u)==0u?low.x:high.x,
                            (i&2u)==0u?low.y:high.y,
                            (i&4u)==0u?low.z:high.z);
                vec4 v=clip*vec4(p,1.0);
                uint mask=(v.x < -v.w?1u:0u)|(v.x>v.w?2u:0u)
                         |(v.y < -v.w?4u:0u)|(v.y>v.w?8u:0u)
                         |(v.z < -v.w?16u:0u)|(v.z>v.w?32u:0u);
                outside&=mask;
            }
            commands[id*5u+1u]=outside==0u?1u:0u;
        }
        """;

    private static final class Key {
        final List<VertexArray> sources;
        final int hash;
        Key(List<VertexArray> sources) {
            this.sources=List.copyOf(sources);
            int h=1;for(var array:sources)h=31*h+System.identityHashCode(array);
            hash=h;
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object x) {
            if(!(x instanceof Key other)||sources.size()!=other.sources.size())return false;
            for(int i=0;i<sources.size();i++)if(sources.get(i)!=other.sources.get(i))return false;
            return true;
        }
    }
    private static final class Piece {
        final int firstIndex,baseVertex,indexCount;
        Piece(int first,int base,int count){firstIndex=first;baseVertex=base;indexCount=count;}
    }
    private static final class Atlas implements AutoCloseable {
        final Map<VertexArray,Piece> pieces=new IdentityHashMap<>();
        int vao,vertices,indices,instances,bounds,commands;
        long fence;
        int indexType;
        boolean available() {
            if(fence==0)return true;
            int state=GL43.glClientWaitSync(fence,0,0L);
            if(state!=GL43.GL_ALREADY_SIGNALED && state!=GL43.GL_CONDITION_SATISFIED)return false;
            GL43.glDeleteSync(fence);fence=0;return true;
        }
        @Override public void close(){
            if(fence!=0)GL43.glDeleteSync(fence);
            if(vao!=0)GL43.glDeleteVertexArrays(vao);
            for(int id:new int[]{vertices,indices,instances,bounds,commands})
                if(id!=0)GL43.glDeleteBuffers(id);
            fence=0;vao=vertices=indices=instances=bounds=commands=0;
        }
    }

    private static int program=-1, countUniform, viewUniform;
    private GpuDrivenMaterialAtlas(){}

    private static boolean eligible(RenderCallData c) {
        if(c.mtr$family()!=RenderPipelineMetrics.Family.TRAIN
            && c.mtr$family()!=RenderPipelineMetrics.Family.RAIL)return false;
        var array=c.mtr$array();
        var state=c.mtr$attributes();
        if(array==null||state==null||state.matrix4f==null||RegionVertexRanges.contains(array)
            ||!c.mtr$visible())return false;
        if(array.materialProperties.translucent||array.materialProperties.cutoutHack
            ||!array.materialProperties.writeDepthBuf
            ||array.materialProperties.vertexAttributeState.matrix4f!=null)return false;
        if(state.position!=null||state.normal!=null||state.textureU!=null||state.textureV!=null)return false;
        if(ModelPartVisibility.gpuBounds(array)==null)return false;
        for(VertexAttributeType t:TYPES) {
            VertexAttributeSource source=array.mapping.sources.get(t);
            if(t==VertexAttributeType.MATRIX_MODEL && source!=VertexAttributeSource.GLOBAL)return false;
            if(source!=VertexAttributeSource.GLOBAL && source!=VertexAttributeSource.VERTEX_BUFFER)return false;
            if(source==VertexAttributeSource.GLOBAL
                && t!=VertexAttributeType.MATRIX_MODEL
                && t!=VertexAttributeType.COLOR
                && t!=VertexAttributeType.UV_LIGHTMAP
                && t!=VertexAttributeType.UV_OVERLAY)return false;
        }
        for(VertexAttributeType t:VARYING)
            if(array.mapping.sources.get(t)==VertexAttributeSource.GLOBAL
                && InstanceValues.value(c,t)==null)return false;
        return true;
    }

    /** One consecutive material/vertex-layout segment may contain multiple
     *  different train or rail models. No sorting across material boundaries. */
    /** Skip the entire atlas discovery walk when the feature is disabled or
     * the batch is too small to contain a worthwhile atlas. */
    public static boolean worthScanning(int callCount) {
        return ENABLED && !failed && callCount >= MIN_CALLS;
    }

    public static boolean candidate(Object object) {
        return ENABLED && !failed && object instanceof RenderCallData call && eligible(call);
    }

    public static boolean sameMaterial(Object left,Object right) {
        if(!(left instanceof RenderCallData a) || !(right instanceof RenderCallData b)
                || !eligible(a) || !eligible(b)) return false;
        var one=a.mtr$array();
        var two=b.mtr$array();
        return a.mtr$family()==b.mtr$family()
                && one.materialProperties==two.materialProperties
                && one.indexBuffer.indexType==two.indexBuffer.indexType
                && one.mapping.strideVertex==two.mapping.strideVertex
                && one.mapping.sources.equals(two.mapping.sources)
                && one.mapping.pointers.equals(two.mapping.pointers);
    }

    public static boolean worthwhile(List<?> calls,int start,int end) {
        if(!ENABLED || failed || end-start<MIN_CALLS)return false;
        var unique=new IdentityHashMap<VertexArray,Boolean>();
        for(int i=start;i<end;i++) {
            if(!(calls.get(i) instanceof RenderCallData call))return false;
            unique.put(call.mtr$array(),Boolean.TRUE);
            if(unique.size()>=2)return true;
        }
        return false;
    }

    /** Called once at the material batch boundary; no partial submissions. */
    public static boolean draw(List<?> calls) {
        if(!ENABLED||failed||calls.size()<MIN_CALLS||calls.size()>LIMIT_CALLS
            ||!Utilities.canUseCustomShader()
            ||org.mtr.mapping.mapper.OptimizedRenderer.renderingShadows()
            ||GlStateTracker.isGl4ES()||!GL.getCapabilities().OpenGL43)return false;
        RenderCallData first=null;
        var unique=new ArrayList<VertexArray>();
        var seen=new IdentityHashMap<VertexArray,Boolean>();
        for(Object object:calls) {
            if(!(object instanceof RenderCallData c)||!eligible(c))return false;
            if(first==null)first=c;
            VertexArray a=c.mtr$array(), base=first.mtr$array();
            if(c.mtr$family()!=first.mtr$family()
                ||a.materialProperties!=base.materialProperties
                ||a.indexBuffer.indexType!=base.indexBuffer.indexType
                ||a.mapping.strideVertex!=base.mapping.strideVertex
                ||!a.mapping.sources.equals(base.mapping.sources)
                ||!a.mapping.pointers.equals(base.mapping.pointers))return false;
            if(seen.put(a,Boolean.TRUE)==null) {
                unique.add(a);
                if(unique.size()>MAX_SOURCES)return false;
            }
        }
        if(unique.size()<2)return false; // Original homogeneous GL instancing is cheaper.
        try {
            Key key=new Key(unique);
            Atlas atlas=ATLASES.get(key);
            if(atlas==null) {
                atlas=create(unique);
                if(atlas==null)return false;
                while(ATLASES.size()>=MAX_ATLASES) {
                    var it=ATLASES.values().iterator();
                    Atlas previous=it.next();
                    if(!previous.available()){atlas.close();return false;}
                    it.remove();previous.close();
                }
                ATLASES.put(key,atlas);misses++;
            }
            if(!atlas.available()||!initialize())return false;
            return submit(atlas,calls,first);
        } catch(RuntimeException error) {
            failed=true;
            Init.LOGGER.error("Solis cross-model GPU atlas disabled; legacy draw path restored",error);
            return false;
        }
    }

    private static Atlas create(List<VertexArray> sources) {
        VertexArray first=sources.get(0);
        int stride=first.mapping.strideVertex,indexSize=switch(first.indexBuffer.indexType) {
            case GL43.GL_UNSIGNED_BYTE -> 1;
            case GL43.GL_UNSIGNED_SHORT -> 2;
            case GL43.GL_UNSIGNED_INT -> 4;
            default -> 0;
        };
        if(stride<=0||indexSize==0)return null;
        int previousVAO=GL43.glGetInteger(GL43.GL_VERTEX_ARRAY_BINDING);
        int previousArray=GL43.glGetInteger(GL43.GL_ARRAY_BUFFER_BINDING);
        int previousCopyRead=GL43.glGetInteger(GL43.GL_COPY_READ_BUFFER_BINDING);
        int previousCopyWrite=GL43.glGetInteger(GL43.GL_COPY_WRITE_BUFFER_BINDING);
        var sizes=new IdentityHashMap<VertexArray,Integer>();
        long vertexBytes=0,indexBytes=0;
        try {
            for(VertexArray source:sources) {
                source.bind();
                int vbo=vertexBuffer(source);
                if(vbo==0)return null;
                GL43.glBindBuffer(GL43.GL_COPY_READ_BUFFER,vbo);
                int bytes=GL43.glGetBufferParameteri(GL43.GL_COPY_READ_BUFFER,GL43.GL_BUFFER_SIZE);
                int indices=source.indexBuffer.getVertexCount();
                if(bytes<=0||bytes%stride!=0||indices<=0)return null;
                sizes.put(source,bytes);vertexBytes+=bytes;indexBytes+=(long)indices*indexSize;
                if(vertexBytes>MAX_VERTEX_BYTES||indexBytes>MAX_INDEX_BYTES)return null;
            }
            Atlas atlas=new Atlas();
            atlas.indexType=first.indexBuffer.indexType;
            try {
                atlas.vertices=GL43.glGenBuffers();
                atlas.indices=GL43.glGenBuffers();
                atlas.instances=GL43.glGenBuffers();
                atlas.bounds=GL43.glGenBuffers();
                atlas.commands=GL43.glGenBuffers();
                GL43.glBindBuffer(GL43.GL_COPY_WRITE_BUFFER,atlas.vertices);
                GL43.glBufferData(GL43.GL_COPY_WRITE_BUFFER,vertexBytes,GL43.GL_STATIC_DRAW);
                GL43.glBindBuffer(GL43.GL_COPY_WRITE_BUFFER,atlas.indices);
                GL43.glBufferData(GL43.GL_COPY_WRITE_BUFFER,indexBytes,GL43.GL_STATIC_DRAW);
                int offsetVertex=0,offsetIndex=0;
                for(VertexArray source:sources) {
                    source.bind();
                    int vbo=vertexBuffer(source);
                    int ibo=GL43.glGetInteger(GL43.GL_ELEMENT_ARRAY_BUFFER_BINDING);
                    if(vbo==0||ibo==0)throw new IllegalStateException("GPU atlas source buffer disappeared");
                    int bytes=sizes.get(source),indices=source.indexBuffer.getVertexCount();
                    GL43.glBindBuffer(GL43.GL_COPY_READ_BUFFER,vbo);
                    GL43.glBindBuffer(GL43.GL_COPY_WRITE_BUFFER,atlas.vertices);
                    GL43.glCopyBufferSubData(GL43.GL_COPY_READ_BUFFER,GL43.GL_COPY_WRITE_BUFFER,
                        0,offsetVertex,bytes);
                    GL43.glBindBuffer(GL43.GL_COPY_READ_BUFFER,ibo);
                    GL43.glBindBuffer(GL43.GL_COPY_WRITE_BUFFER,atlas.indices);
                    GL43.glCopyBufferSubData(GL43.GL_COPY_READ_BUFFER,GL43.GL_COPY_WRITE_BUFFER,
                        0,(long)offsetIndex*indexSize,(long)indices*indexSize);
                    atlas.pieces.put(source,new Piece(offsetIndex,offsetVertex/stride,indices));
                    offsetVertex+=bytes;offsetIndex+=indices;
                }
                atlas.vao=GL43.glGenVertexArrays();
                GL43.glBindVertexArray(atlas.vao);
                GL43.glBindBuffer(GL43.GL_ELEMENT_ARRAY_BUFFER,atlas.indices);
                GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER,atlas.vertices);
                for(VertexAttributeType t:TYPES)if(first.mapping.sources.get(t)==VertexAttributeSource.VERTEX_BUFFER) {
                    t.toggleAttributeArray(true);
                    t.setupAttributePointer(stride,first.mapping.pointers.get(t));
                    for(int k=0;k<t.span;k++)GL43.glVertexAttribDivisor(t.location+k,0);
                }
                GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER,atlas.instances);
                VertexAttributeType.MATRIX_MODEL.toggleAttributeArray(true);
                VertexAttributeType.MATRIX_MODEL.setupAttributePointer(INSTANCE_BYTES,0);
                for(int k=0;k<4;k++)GL43.glVertexAttribDivisor(VertexAttributeType.MATRIX_MODEL.location+k,1);
                int[] offsets={64,68,72};
                for(int i=0;i<VARYING.length;i++) {
                    VertexAttributeType t=VARYING[i];
                    if(first.mapping.sources.get(t)!=VertexAttributeSource.GLOBAL)continue;
                    t.toggleAttributeArray(true);
                    t.setupAttributePointer(INSTANCE_BYTES,offsets[i]);
                    GL43.glVertexAttribDivisor(t.location,1);
                }
                return atlas;
            } catch(RuntimeException|Error error) {atlas.close();throw error;}
        } finally {
            GL43.glBindVertexArray(previousVAO);
            GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER,previousArray);
            GL43.glBindBuffer(GL43.GL_COPY_READ_BUFFER,previousCopyRead);
            GL43.glBindBuffer(GL43.GL_COPY_WRITE_BUFFER,previousCopyWrite);
        }
    }

    private static int vertexBuffer(VertexArray source) {
        for(VertexAttributeType t:TYPES)
            if(source.mapping.sources.get(t)==VertexAttributeSource.VERTEX_BUFFER) {
                int id=GL43.glGetVertexAttribi(t.location,GL43.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
                if(id==0)return 0;
                // A custom non-interleaved vertex mapping cannot be safely copied.
                for(VertexAttributeType other:TYPES)
                    if(source.mapping.sources.get(other)==VertexAttributeSource.VERTEX_BUFFER
                        && GL43.glGetVertexAttribi(other.location,GL43.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING)!=id)
                        return 0;
                return id;
            }
        return 0;
    }

    private static boolean initialize() {
        if(program>0)return true;
        int shader=0,linked=0;
        try {
            shader=GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL43.glShaderSource(shader,COMPUTE);
            GL43.glCompileShader(shader);
            if(GL43.glGetShaderi(shader,GL43.GL_COMPILE_STATUS)==0)
                throw new IllegalStateException("Solis atlas compute: "+GL43.glGetShaderInfoLog(shader));
            linked=GL43.glCreateProgram();
            GL43.glAttachShader(linked,shader);
            GL43.glLinkProgram(linked);
            if(GL43.glGetProgrami(linked,GL43.GL_LINK_STATUS)==0)
                throw new IllegalStateException("Solis atlas link: "+GL43.glGetProgramInfoLog(linked));
            program=linked;
            countUniform=GL43.glGetUniformLocation(linked,"mtr_Count");
            viewUniform=GL43.glGetUniformLocation(linked,"mtr_ViewProjection");
            return true;
        } catch(RuntimeException exception) {
            Init.LOGGER.error("Solis GPU atlas compute shader unavailable",exception);
            if(linked!=0)GL43.glDeleteProgram(linked);
            failed=true;return false;
        } finally {if(shader!=0)GL43.glDeleteShader(shader);}
    }

    /** The caller must never retry ordinary draws after GL submission. */
    private static boolean submit(Atlas atlas,List<?> calls,RenderCallData first) {
        RECORDS.clear();BOUNDS.clear();COMMANDS.clear();
        for(int i=0;i<calls.size();i++) {
            RenderCallData c=(RenderCallData)calls.get(i);
            int base=i*INSTANCE_BYTES;
            FloatBuffer view=MATRIX_VIEWS.slice(i*WORDS,16);
            Utilities.store(c.mtr$attributes().matrix4f,view);
            int color=number(c,VertexAttributeType.COLOR,-1);
            int light=number(c,VertexAttributeType.UV_LIGHTMAP,0);
            int overlay=number(c,VertexAttributeType.UV_OVERLAY,0);
            RECORDS.put(base+64,(byte)(color>>>24));
            RECORDS.put(base+65,(byte)(color>>>16));
            RECORDS.put(base+66,(byte)(color>>>8));
            RECORDS.put(base+67,(byte)color);
            RECORDS.putShort(base+68,(short)(light>>>16));
            RECORDS.putShort(base+70,(short)light);
            RECORDS.putShort(base+72,(short)(overlay>>>16));
            RECORDS.putShort(base+74,(short)overlay);
            float[] extent=ModelPartVisibility.gpuBounds(c.mtr$array());
            BOUNDS.putFloat(extent[0]).putFloat(extent[1]).putFloat(extent[2]).putFloat(0);
            BOUNDS.putFloat(extent[3]).putFloat(extent[4]).putFloat(extent[5]).putFloat(0);
            Piece part=atlas.pieces.get(c.mtr$array());
            COMMANDS.putInt(part.indexCount).putInt(1).putInt(part.firstIndex)
                .putInt(part.baseVertex).putInt(i);
        }
        RECORDS.position(0).limit(calls.size()*INSTANCE_BYTES);
        BOUNDS.flip();COMMANDS.flip();
        int prevVAO=GL43.glGetInteger(GL43.GL_VERTEX_ARRAY_BINDING);
        int prevProgram=GL43.glGetInteger(GL43.GL_CURRENT_PROGRAM);
        int prevArray=GL43.glGetInteger(GL43.GL_ARRAY_BUFFER_BINDING);
        int prevSSBO=GL43.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int prevIndirect=GL43.glGetInteger(GL43.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int[] bindings=new int[3];
        for(int i=0;i<3;i++)bindings[i]=GL43.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);
        boolean submitted=false;
        try {
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,atlas.instances);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,RECORDS,GL43.GL_STREAM_DRAW);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,atlas.bounds);
            GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,BOUNDS,GL43.GL_STREAM_DRAW);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,atlas.commands);
            GL43.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER,COMMANDS,GL43.GL_STREAM_DRAW);
            CLIP.clear();
            new org.joml.Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).get(CLIP);
            CLIP.position(0);
            GL43.glUseProgram(program);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,atlas.instances);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,atlas.bounds);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,2,atlas.commands);
            GL43.glUniform1ui(countUniform,calls.size());
            GL43.glUniformMatrix4fv(viewUniform,false,CLIP);
            GL43.glDispatchCompute((calls.size()+63)/64,1,1);
            GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT|GL43.GL_SHADER_STORAGE_BARRIER_BIT
                |GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            GL43.glUseProgram(prevProgram);
            first.mtr$attributes().apply();
            first.mtr$array().materialProperties.vertexAttributeState.apply();
            GL43.glBindVertexArray(atlas.vao);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,atlas.commands);
            RenderPipelineMetrics.drawing(first.mtr$family());
            GL43.glMultiDrawElementsIndirect(GL43.GL_TRIANGLES,atlas.indexType,0L,calls.size(),0);
            submitted=true;
            atlasedCalls+=calls.size();atlasedDraws++;
            if(atlas.fence!=0)GL43.glDeleteSync(atlas.fence);
            atlas.fence=GL43.glFenceSync(GL43.GL_SYNC_GPU_COMMANDS_COMPLETE,0);
            RenderPipelineMetrics.instancedDraw(0,0);
        } catch(RuntimeException exception) {
            failed=true;
            Init.LOGGER.error("Solis GPU atlas draw failed; disable future attempts",exception);
        } finally {
            for(int i=0;i<3;i++)GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,bindings[i]);
            GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,prevSSBO);
            GL43.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER,prevIndirect);
            GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER,prevArray);
            GL43.glBindVertexArray(prevVAO);
            GL43.glUseProgram(prevProgram);
            GlVertexAttributeCache.invalidate();
        }
        return submitted;
    }

    private static int number(RenderCallData call,VertexAttributeType t,int fallback) {
        Integer value=InstanceValues.value(call,t);
        return value==null?fallback:value;
    }

    public static void clear() {
        if(!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(GpuDrivenMaterialAtlas::clear);
            return;
        }
        ATLASES.values().forEach(Atlas::close);
        ATLASES.clear();
        if(program>0)GL43.glDeleteProgram(program);
        program=-1;failed=false;atlasedCalls=atlasedDraws=misses=0;
    }
    public static void report() {
        if(atlasedDraws>0)Init.LOGGER.info(
            "Solis GPU atlas: multi-draw submissions={}, separate models={}, atlas misses={}, retained-atlases={}",
            atlasedDraws,atlasedCalls,misses,ATLASES.size());
        atlasedCalls=atlasedDraws=misses=0;
    }
}
