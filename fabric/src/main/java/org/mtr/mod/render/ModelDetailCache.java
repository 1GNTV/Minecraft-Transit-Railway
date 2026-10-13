package org.mtr.mod.render;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL33;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.model.*;
import org.mtr.mapping.render.object.*;
import org.mtr.mapping.render.vertex.*;
import org.mtr.mapping.render.tool.GlStateTracker;
import java.lang.ref.Cleaner;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** CPU detail preparation never runs on the render thread. Original geometry stays available. */
public final class ModelDetailCache {
    private static final boolean ENABLED=!"false".equalsIgnoreCase(System.getProperty("mtr.next.lod","true"));
    // Static rail pages require explicit opt-in because simplifying adjacent
    // meshes can reveal seams. The train LOD path remains unchanged.
    private static final boolean RAIL_LOD=!"false".equalsIgnoreCase(System.getProperty("mtr.engine.railLod","true"));
    private static final Map<Mesh,RawMesh> PENDING=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<VertexArray,Owner> LEVELS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Cleaner CLEANER=Cleaner.create();
    private static final AtomicInteger BYTES=new AtomicInteger(),CPU_VERTICES=new AtomicInteger();
    private static final int MAX_BYTES=8*1024*1024,MAX_CPU_VERTICES=100_000;
    private static final ConcurrentLinkedQueue<Owner> READY=new ConcurrentLinkedQueue<>();
    private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread thread=new Thread(r,"MTR geometry detail preparation");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    static { WORKERS.allowCoreThreadTimeOut(true); }
    private static double queuedDistance=Double.NaN;
    private static int uploadDepth,passHeight;
    private static double passProjectionY;
    private static boolean passPerspective;
    private static final IntBuffer VIEWPORT=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();
    private record Indices(int[] main,int[] shadow){}
    private static final class Preparation extends FutureTask<Indices>{
        final int vertices;final Owner owner;final AtomicInteger reservation;
        Preparation(RawMesh snapshot,Owner owner){super(()->{
            if(Thread.currentThread().isInterrupted())return null;
            int[] main=BoundedMeshSimplifier.simplify(snapshot,.125);
            if(Thread.currentThread().isInterrupted())return null;
            int[] shadow=BoundedMeshSimplifier.simplify(snapshot,.25);
            if(main==null)main=ModelDetailGeometry.indices(snapshot,.125);
            if(Thread.currentThread().isInterrupted())return null;
            if(shadow==null)shadow=ModelDetailGeometry.indices(snapshot,.25);
            return new Indices(main,shadow);
        });this.owner=owner;vertices=snapshot.vertices.size();reservation=new AtomicInteger(vertices);}
        void release(){CPU_VERTICES.addAndGet(-reservation.getAndSet(0));}
        @Override public void run(){try{super.run();}finally{release();}}
        @Override protected void done(){if(!isCancelled())READY.add(owner);}
        void stop(){cancel(true);if(WORKERS.remove(this))release();}
    }
    private static final class Owner implements Runnable{
        final java.lang.ref.WeakReference<VertexArray> original;
        Owner(VertexArray original){this.original=new java.lang.ref.WeakReference<>(original);}
        double radius;VertexArray main,shadow;IndexBuffer mainIndices,shadowIndices;Mesh source;VertexAttributeMapping mapping;Preparation task;
        int bytes;Cleaner.Cleanable cleanup;volatile boolean closed;
        @Override public synchronized void run(){
            if(closed)return;closed=true;if(task!=null)task.stop();task=null;source=null;mapping=null;
            int released=bytes;bytes=0;BYTES.addAndGet(-released);
            if(RenderSystem.isOnRenderThread())dispose();else RenderSystem.recordRenderCall(this::dispose);
        }
        private synchronized void dispose(){if(main!=null)main.close();if(shadow!=null)shadow.close();if(mainIndices!=null)mainIndices.close();if(shadowIndices!=null)shadowIndices.close();main=shadow=null;mainIndices=shadowIndices=null;}
    }
    private ModelDetailCache(){}
    public static void beginVehicleUpload(){uploadDepth++;}
    public static void endVehicleUpload(){uploadDepth--;}
    public static double pushDistance(double value){double previous=queuedDistance;queuedDistance=value;return previous;}
    public static void restoreDistance(double value){queuedDistance=value;}
    public static double captureDistance(){return queuedDistance;}
    public static void uploaded(Mesh mesh,RawMesh raw){if(uploadDepth>0)PENDING.put(mesh,raw);}
    public static void attach(VertexArray original,Mesh mesh,VertexAttributeMapping mapping){
        RawMesh raw=PENDING.remove(mesh);if(raw==null)return;ModelPartVisibility.retain(original,raw);
        if(!ENABLED || raw.faces.size()<16 || raw.faces.size()>200_000 || raw.vertices.size()>MAX_CPU_VERTICES || raw.materialProperties.translucent || raw.materialProperties.cutoutHack || !raw.materialProperties.writeDepthBuf)return;
        if(!org.mtr.mapping.render.tool.Utilities.canUseCustomShader() && "false".equalsIgnoreCase(System.getProperty("mtr.next.shaderLod","true")))return;
        int vertices=raw.vertices.size(),reserved=raw.faces.size()*24;
        if(BYTES.addAndGet(reserved)>MAX_BYTES){BYTES.addAndGet(-reserved);return;}
        if(CPU_VERTICES.addAndGet(vertices)>MAX_CPU_VERTICES){CPU_VERTICES.addAndGet(-vertices);BYTES.addAndGet(-reserved);return;}
        Owner owner=new Owner(original);owner.bytes=reserved;owner.source=mesh;owner.mapping=mapping;
        try{
            RawMesh snapshot=RailBatchGeometry.copy(raw);
            for(var vertex:snapshot.vertices){var position=vertex.position;if(position!=null)owner.radius=Math.max(owner.radius,Math.sqrt(position.getX()*position.getX()+position.getY()*position.getY()+position.getZ()*position.getZ()));}
            owner.task=new Preparation(snapshot,owner);owner.cleanup=CLEANER.register(original,owner);LEVELS.put(original,owner);
            WORKERS.execute(owner.task);
        }catch(RejectedExecutionException error){owner.task.release();LEVELS.remove(original);owner.run();}
        catch(RuntimeException|Error error){if(owner.task!=null)owner.task.release();else CPU_VERTICES.addAndGet(-vertices);LEVELS.remove(original);owner.run();throw error;}
    }
    /** Called before the optimized pass, so GL state snapshots cannot overwrite its outer guard. */
    public static void beginPass(){
        if(!ENABLED)return;
        Owner ready=READY.poll();
        if(ready!=null && !ready.closed){
            GlStateTracker.capture();
            try{finish(ready);}catch(RuntimeException error){ready.run();org.mtr.mod.Init.LOGGER.error("MTR detail upload retained original model",error);}finally{GlStateTracker.restore();GlVertexAttributeCache.invalidate();}
        }
        org.joml.Matrix4f projection=RenderSystem.getProjectionMatrix();VIEWPORT.clear();GL33.glGetIntegerv(GL33.GL_VIEWPORT,VIEWPORT);passHeight=VIEWPORT.get(3);passProjectionY=projection.m11();passPerspective=Math.abs(projection.m33())<.5;
    }
    private static void finish(Owner owner){synchronized(owner){
        if(owner.closed || owner.task==null || !owner.task.isDone())return;
        Indices result;try{result=owner.task.get();}catch(Exception ignored){owner.run();return;}
        owner.task=null;
        VertexArray original=owner.original.get();if(original==null){owner.run();return;}
        if(result==null || result.main==null && result.shadow==null){owner.run();return;}
        int actual=(result.main==null?0:result.main.length*4)+(result.shadow==null?0:result.shadow.length*4);
        BYTES.addAndGet(actual-owner.bytes);owner.bytes=actual;Mesh source=owner.source;VertexAttributeMapping mapping=owner.mapping;
        if(result.main!=null){owner.mainIndices=upload(result.main);owner.main=new VertexArray(new Mesh(source.vertexBuffer,owner.mainIndices,source.materialProperties),mapping);}
        if(result.shadow!=null){owner.shadowIndices=upload(result.shadow);owner.shadow=new VertexArray(new Mesh(source.vertexBuffer,owner.shadowIndices,source.materialProperties),mapping);}
        // Bounds/LOD selection refer to the original conservative source; no retained raw mesh is required.
        if(owner.main!=null)ModelPartVisibility.copy(original,owner.main);if(owner.shadow!=null)ModelPartVisibility.copy(original,owner.shadow);
        owner.source=null;owner.mapping=null;
    }}
    private static IndexBuffer upload(int[] indices){IndexBuffer index=new IndexBuffer(indices.length/3,GL33.GL_UNSIGNED_INT);ByteBuffer data=ByteBuffer.allocateDirect(indices.length*4).order(ByteOrder.nativeOrder());data.asIntBuffer().put(indices);try{index.upload(data,VertexBuffer.USAGE_STATIC_DRAW);return index;}catch(RuntimeException|Error error){index.close();throw error;}}
    public static VertexArray select(VertexArray original,RenderPipelineMetrics.Family family,double distanceSquared){
        if(!ENABLED || (family!=RenderPipelineMetrics.Family.TRAIN
                && !(RAIL_LOD && family==RenderPipelineMetrics.Family.RAIL))
                || !Double.isFinite(distanceSquared))return original;
        Owner owner=LEVELS.get(original);if(owner==null || owner.closed)return original;
        if(!org.mtr.mapping.render.tool.Utilities.canUseCustomShader() && "false".equalsIgnoreCase(System.getProperty("mtr.next.shaderLod","true")))return original;
        boolean shadows=OptimizedRenderer.renderingShadows();double error=(shadows?.25:.125)*Math.sqrt(3),pixels=shadows?1:.75;
        if(!ScreenErrorBudget.permits(error,distanceSquared,owner.radius,passProjectionY,passHeight,passPerspective,pixels))return original;
        VertexArray level=shadows?owner.shadow:owner.main;return level==null?original:level;
    }
    public static void close(VertexArray original){Owner owner=LEVELS.remove(original);if(owner!=null)owner.cleanup.clean();ModelPartVisibility.remove(original);}
    public static void clear(){synchronized(LEVELS){LEVELS.values().forEach(Owner::run);LEVELS.clear();}PENDING.clear();READY.clear();WORKERS.purge();}
}
