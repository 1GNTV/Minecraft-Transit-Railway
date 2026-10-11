package org.mtr.mod.render;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL33;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.object.*;
import org.mtr.mapping.render.vertex.*;
import java.lang.ref.Cleaner;
import java.nio.*;
import java.util.*;

/** Two index-only LODs sharing the original immutable vertex buffer. */
public final class ModelDetailCache {
    private static final boolean ENABLED=!"false".equalsIgnoreCase(System.getProperty("mtr.next.lod","true"));
    private static final double MAIN_DISTANCE=positive("mtr.next.lodDistance",160),SHADOW_DISTANCE=positive("mtr.next.shadowLodDistance",64);
    private static final Map<Mesh,RawMesh> PENDING=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<VertexArray,Owner> LEVELS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Cleaner CLEANER=Cleaner.create();
    private static final java.util.concurrent.atomic.AtomicInteger BYTES=new java.util.concurrent.atomic.AtomicInteger();
    private static final int MAX_BYTES=8*1024*1024;
    private static double queuedDistance=Double.NaN;
    private static int uploadDepth;
    private static int passHeight;
    private static double passProjectionY;
    private static boolean passPerspective;
    public static void beginPass(){if(!ENABLED)return;org.joml.Matrix4f projection=RenderSystem.getProjectionMatrix();VIEWPORT.clear();GL33.glGetIntegerv(GL33.GL_VIEWPORT,VIEWPORT);passHeight=VIEWPORT.get(3);passProjectionY=projection.m11();passPerspective=Math.abs(projection.m33())<.5;}
    private static final java.nio.IntBuffer VIEWPORT=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();
    public static void beginVehicleUpload(){uploadDepth++;}
    public static void endVehicleUpload(){uploadDepth--;}
    private static final class Owner implements Runnable{
        double radius;
        VertexArray main,shadow;IndexBuffer mainIndices,shadowIndices;int bytes;Cleaner.Cleanable cleanup;boolean closed;
        @Override public synchronized void run(){if(closed)return;closed=true;if(main!=null)main.close();if(shadow!=null)shadow.close();if(mainIndices!=null)mainIndices.close();if(shadowIndices!=null)shadowIndices.close();BYTES.addAndGet(-bytes);}
    }
    private ModelDetailCache(){}
    private static double positive(String name,double fallback){try{double n=Double.parseDouble(System.getProperty(name,""));return Double.isFinite(n)&&n>=32?n:fallback;}catch(Exception ignored){return fallback;}}
    public static double pushDistance(double value){double previous=queuedDistance;queuedDistance=value;return previous;}
    public static void restoreDistance(double value){queuedDistance=value;}
    public static double captureDistance(){return queuedDistance;}
    public static void uploaded(Mesh mesh,RawMesh raw){if(uploadDepth>0)PENDING.put(mesh,raw);}
    public static void attach(VertexArray original,Mesh mesh,VertexAttributeMapping mapping){
        RawMesh raw=PENDING.remove(mesh);if(raw==null)return;
        ModelPartVisibility.retain(original,raw);
        if(!ENABLED || BYTES.get()>=MAX_BYTES)return;
        int[] main=BoundedMeshSimplifier.simplify(raw,0.125),shadow=BoundedMeshSimplifier.simplify(raw,0.25);
        if(main==null)main=ModelDetailGeometry.indices(raw,0.125);
        if(shadow==null)shadow=ModelDetailGeometry.indices(raw,0.25);
        if(main==null && shadow==null)return;
        int bytes=(main==null?0:main.length*4)+(shadow==null?0:shadow.length*4);if(BYTES.get()+bytes>MAX_BYTES)return;
        Owner owner=new Owner();BYTES.addAndGet(bytes);owner.bytes=bytes;
        for(var vertex:raw.vertices){var p=vertex.position;if(p!=null)owner.radius=Math.max(owner.radius,Math.sqrt(p.getX()*p.getX()+p.getY()*p.getY()+p.getZ()*p.getZ()));}
        try{
            if(main!=null){owner.mainIndices=upload(main);owner.main=new VertexArray(new Mesh(mesh.vertexBuffer,owner.mainIndices,mesh.materialProperties),mapping);ModelPartVisibility.retain(owner.main,raw);}
            if(shadow!=null){owner.shadowIndices=upload(shadow);owner.shadow=new VertexArray(new Mesh(mesh.vertexBuffer,owner.shadowIndices,mesh.materialProperties),mapping);ModelPartVisibility.retain(owner.shadow,raw);}
            owner.cleanup=CLEANER.register(original,owner);LEVELS.put(original,owner);
        }catch(RuntimeException|Error error){owner.run();throw error;}
    }
    private static IndexBuffer upload(int[] indices){IndexBuffer index=new IndexBuffer(indices.length/3,GL33.GL_UNSIGNED_INT);ByteBuffer data=ByteBuffer.allocateDirect(indices.length*4).order(ByteOrder.nativeOrder());data.asIntBuffer().put(indices);try{index.upload(data,VertexBuffer.USAGE_STATIC_DRAW);return index;}catch(RuntimeException|Error error){index.close();throw error;}}
    public static VertexArray select(VertexArray original,RenderPipelineMetrics.Family family,double distanceSquared){
        if(!ENABLED || family!=RenderPipelineMetrics.Family.TRAIN || !Double.isFinite(distanceSquared))return original;
        Owner owner=LEVELS.get(original);if(owner==null || owner.closed)return original;
        // Shader-pack vertex deformation has no reliable source-space error bound.
        if(!org.mtr.mapping.render.tool.Utilities.canUseCustomShader() && !Boolean.getBoolean("mtr.next.shaderLod"))return original;
        boolean shadows=OptimizedRenderer.renderingShadows();
        double error=(shadows?.25:.125)*Math.sqrt(3),pixels=shadows?1.0:.75;
        if(!ScreenErrorBudget.permits(error,distanceSquared,owner.radius,passProjectionY,passHeight,passPerspective,pixels))return original;
        VertexArray level=shadows?owner.shadow:owner.main;return level==null?original:level;
    }
    public static void close(VertexArray original){Owner owner=LEVELS.remove(original);if(owner!=null)owner.cleanup.clean();ModelPartVisibility.remove(original);}
}
