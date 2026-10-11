package org.mtr.mod.resource;

import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.obj.ObjModelLoader;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.mod.mixin.ModelAtlasAccessor;
import org.mtr.mod.mixin.PreparedObjModelAccessor;
import java.util.*;
import java.util.concurrent.*;

/** Workers own immutable source snapshots, never a world/resource manager or GL context. */
public final class ModelPreparation {
    public record Prepared(BlockbenchModel blockbench, Map<String,List<RawMesh>> meshes) {}
    private record Job(Future<Prepared> future, int chars, long created, java.util.concurrent.atomic.AtomicInteger reservation, java.util.concurrent.atomic.AtomicInteger budget) {}
    private static java.util.concurrent.atomic.AtomicInteger geometryBudget=new java.util.concurrent.atomic.AtomicInteger();
    private static final Map<VehicleModel,Job> JOBS = new LinkedHashMap<>();
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"MTR model CPU preparation");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static int retainedChars, requestsLeft=2;
    public static void beginFrame(){requestsLeft=2;}
    static { WORKERS.allowCoreThreadTimeOut(true); }
    private ModelPreparation() {}

    public static void request(VehicleModel key,String name,Identifier texture,ResourceProvider provider) {
        long now=System.currentTimeMillis();var expired=JOBS.values().iterator();while(expired.hasNext()){Job job=expired.next();if(now-job.created>5000){release(job);retainedChars-=job.chars;expired.remove();}}
        if ("false".equalsIgnoreCase(System.getProperty("mtr.next.preload","true")) || JOBS.containsKey(key) || key.cachedModel.hasData() || JOBS.size()>=18 || requestsLeft<=0) return;
        requestsLeft--;
        final Map<String,String> materials=new HashMap<>();
        final String content;
        if (name.endsWith(".mqoz")) content=ModelResourceLoader.extractMqoContentFromMqoz(name,provider.getBytes(CustomResourceTools.formatIdentifierWithDefault(name,"mqoz")));
        else content=provider.get(CustomResourceTools.formatIdentifierWithDefault(name,name.endsWith(".bbmodel")?"bbmodel":name.endsWith(".mqo")?"mqo":"obj"));
        int chars=content.length();
        if (chars>2_000_000 || retainedChars+chars>8_000_000) return;
        if (name.endsWith(".obj")) {
            // Snapshot MTL files here; worker resolver cannot access the game's resource manager.
            for (String line:content.split("\\R")) {
                String clean=line.strip();
                if (!clean.startsWith("mtllib ") && !clean.startsWith("mtllib\t")) continue;
                for(String file:clean.substring(6).strip().split("\\s+")) {
                    String normalized=file.replace('\\','/');
                    String value=provider.get(CustomResourceTools.getResourceFromSamePath(name,normalized,"mtl"));
                    materials.put(normalized,value); chars+=value.length();
                }
            }
        }
        if (chars>4_000_000 || retainedChars+chars>8_000_000) return;
        final Map<String,String> snapshot=Map.copyOf(materials);
        try {
            final var budget=geometryBudget;final var reservation=new java.util.concurrent.atomic.AtomicInteger();
            Future<Prepared> future=WORKERS.submit(()->{
                if (name.endsWith(".bbmodel")) return new Prepared(new BlockbenchModel(new JsonReader(Utilities.parseJson(content))),null);
                String obj=content,mtl=null;
                if (name.endsWith(".mqo") || name.endsWith(".mqoz")) { var converted=MqoModelConverter.convert(content);obj=converted.getObjContent();mtl=converted.getMtlContent(); }
                final String convertedMtl=mtl;
                Map<String,List<RawMesh>> meshes=ObjModelLoader.loadModel(obj,file->convertedMtl!=null?convertedMtl:snapshot.getOrDefault(file,""),file->file.isEmpty()?OptimizedModelWrapper.WHITE_TEXTURE:file.equals("default.png")?texture:CustomResourceTools.getResourceFromSamePath(name,file,"png"),null,true);
                long vertices=meshes.values().stream().flatMap(List::stream).mapToLong(mesh->mesh.vertices.size()).sum();
                if (vertices>200_000 || meshes.isEmpty() || Thread.currentThread().isInterrupted()) return null;
                if(budget.addAndGet((int)vertices)>500_000){budget.addAndGet(-(int)vertices);return null;}
                reservation.set((int)vertices);
                if(Thread.currentThread().isInterrupted()){budget.addAndGet(-reservation.getAndSet(0));return null;}
                return new Prepared(null,meshes);
            });
            JOBS.put(key,new Job(future,chars,now,reservation,budget));retainedChars+=chars;
        } catch(RejectedExecutionException ignored) { /* bounded queue: ordinary loading remains available */ }
    }

    public static Prepared take(VehicleModel key) {
        Job job=JOBS.remove(key);if(job==null)return null;
        retainedChars-=job.chars;
        if(!job.future.isDone()){release(job);return null;}
        job.budget.addAndGet(-job.reservation.getAndSet(0));
        try{return job.future.get();}catch(Exception ignored){return null;}
    }
    public static Object2ObjectAVLTreeMap<String,OptimizedModel.ObjModel> finish(Prepared prepared,boolean flipV) {
        final Object2ObjectAVLTreeMap<String,OptimizedModel.ObjModel> result=new Object2ObjectAVLTreeMap<>();
        prepared.meshes.forEach((name,meshes)->{
            float[] b={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
            for(RawMesh mesh:meshes){
                // Atlas mutations stay on the render thread; preserve original atlas/rotation/UV order.
                ModelAtlasAccessor.mtr$atlas().applyToMesh(mesh);
                mesh.applyRotation(new Vector3f(1,0,0),180);
                mesh.vertices.forEach(v->{float x=v.position.getX(),y=v.position.getY(),z=v.position.getZ();b[0]=Math.min(b[0],x);b[1]=Math.min(b[1],y);b[2]=Math.min(b[2],z);b[3]=Math.max(b[3],x);b[4]=Math.max(b[4],y);b[5]=Math.max(b[5],z);});
            }
            result.put(name,PreparedObjModelAccessor.mtr$create(meshes,flipV,b[0],b[1],b[2],b[3],b[4],b[5]));
        });return result;
    }
    private static void release(Job job){job.future.cancel(true);job.budget.addAndGet(-job.reservation.getAndSet(0));}
    public static void clear(){JOBS.values().forEach(ModelPreparation::release);JOBS.clear();retainedChars=0;geometryBudget=new java.util.concurrent.atomic.AtomicInteger();WORKERS.purge();}
}
